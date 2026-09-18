package com.itilms.liveclass.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.liveclass.config.LiveClassProperties;
import com.itilms.liveclass.entity.LiveParticipant;
import com.itilms.liveclass.entity.LiveParticipantEventLog;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.LiveSessionStatus;
import com.itilms.liveclass.entity.ParticipantRole;
import com.itilms.liveclass.livekit.WebhookNotice;
import com.itilms.liveclass.repository.LiveParticipantEventRepository;
import com.itilms.liveclass.repository.LiveParticipantRepository;
import com.itilms.liveclass.repository.LiveSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies what LiveKit reports about a room to this service's records.
 *
 * <p>The notice has already been authenticated by the time it reaches here; see
 * {@code LiveKitWebhookController}. What this class guards against is not forgery
 * but the ordinary untidiness of webhooks: duplicates, deliveries for rooms this
 * service did not create, and notices that arrive after the class was settled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveKitWebhookHandler {

    private final LiveSessionRepository sessionRepository;
    private final LiveParticipantRepository participantRepository;
    private final LiveParticipantEventRepository eventRepository;
    private final LiveAttendanceService attendanceService;
    private final EventPublisher events;
    private final LiveClassProperties props;

    @Transactional
    public void handle(WebhookNotice notice) {
        if (!notice.isHandled() || notice.roomName() == null) {
            return;
        }

        // Locking the room row serialises every notice for one class. That is
        // what stops two simultaneous joins both reading a peak of 11 and both
        // writing 12, and the final notice racing the sweep to settle twice.
        LiveSession session = sessionRepository.findByRoomNameForUpdate(notice.roomName()).orElse(null);
        if (session == null) {
            // Rooms created by hand on the LiveKit console, or by another
            // application sharing the server. Not ours to account for.
            log.debug("Ignoring {} for unrecognised room {}", notice.event(), notice.roomName());
            return;
        }

        if (notice.id() != null && eventRepository.existsByLivekitEventId(notice.id())) {
            log.debug("Duplicate delivery {} for room {} ignored", notice.id(), notice.roomName());
            return;
        }

        eventRepository.save(LiveParticipantEventLog.builder()
                .liveSessionId(session.getId())
                .identity(notice.identity())
                .userId(userIdFrom(notice.identity()))
                .eventType(notice.event())
                .participantSid(notice.participantSid())
                .occurredAt(notice.occurredAt())
                .livekitEventId(notice.id())
                .build());

        switch (notice.event()) {
            case WebhookNotice.ROOM_STARTED -> onRoomStarted(session, notice);
            case WebhookNotice.PARTICIPANT_JOINED -> onJoined(session, notice);
            case WebhookNotice.PARTICIPANT_LEFT -> onLeft(session, notice);
            case WebhookNotice.ROOM_FINISHED -> onRoomFinished(session, notice);
            default -> {
                // isHandled() already filtered everything else out.
            }
        }
    }

    private void onRoomStarted(LiveSession session, WebhookNotice notice) {
        if (session.getLivekitRoomSid() == null && notice.roomSid() != null) {
            session.setLivekitRoomSid(notice.roomSid());
            sessionRepository.save(session);
        }
    }

    /**
     * LiveKit closed the room. Whether that ends the class depends on when.
     *
     * <p>LiveKit closes a room for two ordinary reasons that are not the end of
     * a lecture: nobody entered in time, or everybody dropped out at once - the
     * institute's internet went down for five minutes at 10:25. Settling on
     * either would publish attendance mid-class and refuse every rejoin with
     * "this class has finished".
     *
     * <p>So only a room that had been in use, closing within the finish margin
     * of the scheduled end, settles here. Any other closure just forgets the room
     * sid; the next join recreates the room and the class carries on. A class
     * that genuinely ended early is still settled - by the trainer's End button,
     * or by the sweep once the slot is over.
     */
    private void onRoomFinished(LiveSession session, WebhookNotice notice) {
        if (isClosed(session)) {
            return;
        }

        Instant finishLine = session.getScheduledEndAt().minus(Duration.ofMinutes(props.getFinishMarginMinutes()));
        boolean classWasRunning = session.getStatus() == LiveSessionStatus.LIVE;

        if (classWasRunning && !notice.occurredAt().isBefore(finishLine)) {
            attendanceService.settle(session.getId(), null);
            return;
        }

        session.setLivekitRoomSid(null);
        sessionRepository.save(session);
        log.info("Room {} closed at {} before the class finished; it will be recreated on the next join",
                session.getRoomName(), notice.occurredAt());
    }

    private void onJoined(LiveSession session, WebhookNotice notice) {
        if (isClosed(session)) {
            return;
        }
        LiveParticipant participant = findParticipant(session, notice);
        if (participant == null) {
            return;
        }

        participant.joined(notice.occurredAt());
        participantRepository.saveAndFlush(participant);

        session.markLive(notice.occurredAt());
        session.notePeak((int) participantRepository.countByLiveSessionIdAndCurrentJoinAtIsNotNull(session.getId()));
        sessionRepository.save(session);

        if (participant.getRole() == ParticipantRole.TRAINER && participant.getJoinCount() == 1) {
            // Students who opened the dashboard early are told the moment
            // teaching can begin, rather than refreshing a page to find out.
            events.notifyBatch(session.getBatchId(), "LIVE_CLASS",
                    "Class is live: " + (session.getBatchCode() == null ? "your batch" : session.getBatchCode()),
                    session.getTopic() == null
                            ? "Your trainer has started the class. Join now."
                            : "Your trainer has started \"" + session.getTopic() + "\". Join now.",
                    "/live/" + session.getClassSessionId());
        }
    }

    private void onLeft(LiveSession session, WebhookNotice notice) {
        if (isClosed(session)) {
            return;
        }
        LiveParticipant participant = findParticipant(session, notice);
        if (participant == null) {
            return;
        }
        participant.left(notice.occurredAt(), session.getScheduledStartAt(), session.getScheduledEndAt());
        participantRepository.save(participant);
    }

    /**
     * Once attendance has been published the totals are frozen.
     *
     * <p>Closing the room fires a leave notice for everyone still inside, and
     * those arrive after settlement. They are logged above, but letting them
     * change the totals would make the figures shown to the trainer disagree
     * with the register batch-service wrote from them.
     */
    private boolean isClosed(LiveSession session) {
        return session.isAttendanceComputed() || session.getStatus() == LiveSessionStatus.CANCELLED;
    }

    private LiveParticipant findParticipant(LiveSession session, WebhookNotice notice) {
        if (notice.identity() == null) {
            return null;
        }
        LiveParticipant participant = participantRepository
                .findByLiveSessionIdAndIdentity(session.getId(), notice.identity())
                .orElse(null);
        if (participant == null) {
            // Every legitimate participant was registered when their token was
            // minted. An identity with no record is a recorder or agent bot, or
            // a token issued outside IT-ILMS - either way, not a student.
            log.warn("Participant {} in room {} has no join record; not counted",
                    notice.identity(), session.getRoomName());
        }
        return participant;
    }

    private Long userIdFrom(String identity) {
        if (identity == null || !identity.startsWith("user-")) {
            return null;
        }
        try {
            return Long.valueOf(identity.substring("user-".length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
