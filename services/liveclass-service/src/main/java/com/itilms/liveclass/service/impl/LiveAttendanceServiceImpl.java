package com.itilms.liveclass.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.LiveAttendanceComputedEvent;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.liveclass.config.LiveClassProperties;
import com.itilms.liveclass.entity.LiveParticipant;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.LiveSessionStatus;
import com.itilms.liveclass.entity.ParticipantRole;
import com.itilms.liveclass.livekit.LiveKitGateway;
import com.itilms.liveclass.repository.LiveParticipantRepository;
import com.itilms.liveclass.repository.LiveSessionRepository;
import com.itilms.liveclass.service.LiveAttendanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Works out who attended an online class, and by how much.
 *
 * <p>The rule, in the order it is applied:
 * <ol>
 *   <li>If no trainer or staff member ever entered, the class did not take
 *       place. Nothing is published. Marking forty students absent from a
 *       lecture nobody gave would be false, and would drag every one of their
 *       percentages below the certificate threshold for the institute's own
 *       failure.</li>
 *   <li>Otherwise each student's time inside the scheduled slot, summed over
 *       every reconnect, is divided by the length of the class as it actually
 *       ran (see {@link LiveSession#attendanceWindowSeconds}).</li>
 *   <li>That share is compared with the configured thresholds: PRESENT at or
 *       above the present threshold, LATE at or above the late threshold,
 *       ABSENT below it.</li>
 * </ol>
 *
 * <p>Students who never asked to join do not appear in the published event.
 * batch-service holds the roster and marks them ABSENT itself; this service
 * only ever knew about the people who tried to come in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveAttendanceServiceImpl implements LiveAttendanceService {

    private static final String SERVICE_NAME = "liveclass-service";

    static final String PRESENT = "PRESENT";
    static final String LATE = "LATE";
    static final String ABSENT = "ABSENT";

    private final LiveSessionRepository sessionRepository;
    private final LiveParticipantRepository participantRepository;
    private final com.itilms.liveclass.repository.LiveQuestionRepository questionRepository;
    private final LiveKitGateway liveKit;
    private final LiveClassProperties props;
    private final EventPublisher events;

    @Override
    @Transactional
    public boolean settle(Long liveSessionId, Instant closedAt) {
        LiveSession session = sessionRepository.findByIdForUpdate(liveSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Live session", liveSessionId));

        if (session.isAttendanceComputed() || session.getStatus() == LiveSessionStatus.CANCELLED) {
            return false;
        }

        List<LiveParticipant> participants =
                participantRepository.findByLiveSessionIdOrderByDisplayNameAsc(liveSessionId);

        Instant closeAt = closedAt != null ? closedAt : inferCloseTime(session, participants);
        session.markEnded(closeAt);
        questionRepository.closeAllOpen(liveSessionId, closeAt);
        if (session.getEgressId() != null) {
            liveKit.stopEgressQuietly(session.getEgressId());
        }

        // Nobody's leave event arrives when the room is torn down around them, or
        // when a laptop lid closes and LiveKit's own timeout is what ends the
        // stint. Their time runs up to the close, clipped to the slot.
        for (LiveParticipant participant : participants) {
            if (participant.inRoom()) {
                participant.left(closeAt, session.getScheduledStartAt(), session.getScheduledEndAt());
            }
        }

        Instant hostArrivedAt = participants.stream()
                .filter(p -> p.getRole() != ParticipantRole.STUDENT)
                .map(LiveParticipant::getFirstJoinedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        session.setAttendanceComputed(true);
        session.setAttendanceComputedAt(Instant.now());

        if (hostArrivedAt == null) {
            participantRepository.saveAll(participants);
            sessionRepository.save(session);
            log.warn("Live session {} (class session {}) closed without a trainer ever joining; "
                    + "no attendance published", session.getId(), session.getClassSessionId());
            events.audit(SERVICE_NAME, "LIVE_CLASS_NOT_HELD", "LiveSession", session.getId(), null,
                    Map.of("classSessionId", session.getClassSessionId(),
                            "studentsWaiting", participants.stream()
                                    .filter(p -> p.getRole() == ParticipantRole.STUDENT && p.getJoinCount() > 0)
                                    .count()));
            return true;
        }

        int windowSeconds = session.attendanceWindowSeconds(hostArrivedAt);
        List<LiveAttendanceComputedEvent.Entry> entries = new ArrayList<>();

        for (LiveParticipant participant : participants) {
            if (!participant.getRole().countsTowardsAttendance() || participant.getStudentId() == null) {
                continue;
            }
            int percent = percentOf(participant.getAttendedSeconds(), windowSeconds);
            String status = verdict(percent);
            participant.applyComputed(percent, status);

            entries.add(new LiveAttendanceComputedEvent.Entry(
                    participant.getStudentId(), participant.getUserId(),
                    participant.getAttendedSeconds(), percent, status));
        }

        participantRepository.saveAll(participants);
        sessionRepository.save(session);

        events.publishAfterCommit(KafkaTopics.LIVE_ATTENDANCE_COMPUTED,
                String.valueOf(session.getClassSessionId()),
                new LiveAttendanceComputedEvent(
                        DomainEvent.newId(), Instant.now(),
                        session.getId(), session.getClassSessionId(), session.getBatchId(),
                        windowSeconds, List.copyOf(entries)));

        log.info("Settled live session {}: {} student record(s) over a {}-minute class",
                session.getId(), entries.size(), windowSeconds / 60);
        return true;
    }

    /**
     * When the class stopped, for a settlement nobody triggered by hand.
     *
     * <p>If the room emptied on its own, the class ended when the last person
     * left - not when LiveKit got round to closing the room minutes later, and
     * not at the scheduled end, which would stretch a class that finished at
     * 10:40 to 11:00 and mark down everyone who left with the trainer.
     *
     * <p>If someone is still shown inside, their leave notice was lost, and the
     * scheduled end is the most that can fairly be assumed. The earlier of that
     * and now is used, since a class cannot have ended in the future.
     */
    static Instant inferCloseTime(LiveSession session, List<LiveParticipant> participants) {
        Instant now = Instant.now();
        Instant scheduledEnd = session.getScheduledEndAt();
        Instant cap = now.isBefore(scheduledEnd) ? now : scheduledEnd;

        boolean anyoneInside = participants.stream().anyMatch(LiveParticipant::inRoom);
        if (anyoneInside) {
            return cap;
        }
        return participants.stream()
                .map(LiveParticipant::getLastLeftAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(cap);
    }

    /**
     * Rounded down, and capped at 100.
     *
     * <p>Rounding down means 69.9% is not PRESENT against a 70% threshold - the
     * threshold means what it says. The cap covers a student who waited in the
     * room before a late trainer arrived: their time can exceed the class that
     * was actually taught, and "130% attendance" helps nobody.
     */
    static int percentOf(int attendedSeconds, int windowSeconds) {
        if (windowSeconds <= 0 || attendedSeconds <= 0) {
            return 0;
        }
        long percent = (long) attendedSeconds * 100 / windowSeconds;
        return (int) Math.min(100, percent);
    }

    String verdict(int percent) {
        if (percent >= props.getPresentThresholdPercent()) {
            return PRESENT;
        }
        if (percent >= props.getLateThresholdPercent()) {
            return LATE;
        }
        return ABSENT;
    }
}
