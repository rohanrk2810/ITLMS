package com.itilms.liveclass.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.SessionScheduledEvent;
import com.itilms.common.exception.ApiException;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.liveclass.client.BatchClient;
import com.itilms.liveclass.config.LiveClassProperties;
import com.itilms.liveclass.dto.response.JoinTokenResponse;
import com.itilms.liveclass.dto.response.LiveParticipantResponse;
import com.itilms.liveclass.dto.response.LiveSessionResponse;
import com.itilms.liveclass.entity.LiveParticipant;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.LiveSessionStatus;
import com.itilms.liveclass.entity.ParticipantRole;
import com.itilms.liveclass.livekit.LiveKitGateway;
import com.itilms.liveclass.livekit.LiveRoomProvisioner;
import com.itilms.liveclass.repository.LiveParticipantRepository;
import com.itilms.liveclass.repository.LiveSessionRepository;
import com.itilms.liveclass.service.LiveAttendanceService;
import com.itilms.liveclass.service.HostAccess;
import com.itilms.liveclass.service.LiveClassService;
import com.itilms.liveclass.service.RoomPermissions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates a live class across three systems: the timetable in
 * batch-service, the room on LiveKit, and this service's own record of who was
 * in it.
 *
 * <p>Most public methods here are deliberately <em>not</em> transactional. A
 * join makes an HTTP call to batch-service and another to LiveKit, and holding a
 * database connection and row locks open across two network round trips is how
 * a class of 60 students clicking Join at 10:00 exhausts the connection pool.
 * Writes happen in short, targeted statements instead, each safe against the
 * race it could meet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveClassServiceImpl implements LiveClassService {

    private static final String SERVICE_NAME = "liveclass-service";

    /** LiveKit identities are {@code user-<userId>}; stable per person, meaningless to anyone else. */
    static final String IDENTITY_PREFIX = "user-";

    private static final String ROOM_PREFIX = "itilms-session-";

    private final LiveSessionRepository sessionRepository;
    private final LiveParticipantRepository participantRepository;
    private final BatchClient batchClient;
    private final LiveKitGateway liveKit;
    private final LiveRoomProvisioner roomProvisioner;
    private final LiveAttendanceService attendanceService;
    private final LiveClassProperties props;
    private final EventPublisher events;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final HostAccess hostAccess;

    // -----------------------------------------------------------------
    // Provisioning
    // -----------------------------------------------------------------

    @Override
    public LiveSession provision(Long classSessionId) {
        return sessionRepository.findByClassSessionId(classSessionId)
                .orElseGet(() -> createFromTimetable(classSessionId));
    }

    private LiveSession createFromTimetable(Long classSessionId) {
        BatchClient.SessionDetail detail = batchClient.session(classSessionId);
        if (detail == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TIMETABLE_UNAVAILABLE",
                    "The timetable could not be reached to open this class. Please try again shortly.");
        }
        if ("CANCELLED".equals(detail.status())) {
            throw new BusinessRuleException("SESSION_CANCELLED", "This class has been cancelled.");
        }
        if (!detail.live()) {
            throw new BusinessRuleException("NOT_A_LIVE_CLASS",
                    "This class is held in a classroom, so it has no live room.");
        }

        LiveSession draft = newSession(detail.id(), detail.batchId(), detail.batchCode(),
                detail.courseTitle(), detail.trainerId(), null, detail.topic(),
                detail.sessionDate(), detail.startTime(), detail.endTime());

        return insertOrFetch(draft);
    }

    @Override
    @Transactional
    public void applySchedule(SessionScheduledEvent event) {
        boolean live = "ONLINE".equals(event.mode()) || "HYBRID".equals(event.mode());
        var existing = sessionRepository.findByClassSessionId(event.sessionId());

        if (existing.isEmpty()) {
            if (!live) {
                return;
            }
            LiveSession draft = newSession(event.sessionId(), event.batchId(), event.batchCode(), null,
                    event.trainerId(), event.trainerUserId(), event.topic(),
                    event.sessionDate(), event.startTime(), event.endTime());
            // If a student's Join created the row a moment earlier, this fails on
            // the unique key; Kafka redelivers and the retry takes the update path.
            sessionRepository.saveAndFlush(draft);
            log.info("Provisioned live room {} for session {}", draft.getRoomName(), event.sessionId());
            return;
        }

        LiveSession session = existing.get();

        if (!live) {
            // Switched to a classroom session. An open room for it would be a
            // second, unattended venue for the same class.
            cancelInPlace(session, "Session moved to classroom delivery");
            return;
        }

        if (session.getStatus() != LiveSessionStatus.SCHEDULED) {
            // A class already running or finished keeps the times it actually
            // ran at; rewriting them would move the attendance window under
            // students who were there.
            log.warn("Ignoring schedule change for live session {} in status {}",
                    session.getId(), session.getStatus());
            return;
        }

        session.setSessionDate(event.sessionDate());
        session.setStartTime(event.startTime());
        session.setEndTime(event.endTime());
        session.setScheduledStartAt(toInstant(event.sessionDate(), event.startTime()));
        session.setScheduledEndAt(toInstant(event.sessionDate(), event.endTime()));
        session.setTopic(event.topic());
        session.setBatchCode(event.batchCode());
        session.setTrainerId(event.trainerId());
        if (event.trainerUserId() != null) {
            session.setTrainerUserId(event.trainerUserId());
        }
        sessionRepository.save(session);
    }

    // -----------------------------------------------------------------
    // Joining
    // -----------------------------------------------------------------

    @Override
    public JoinTokenResponse join(Long classSessionId) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        LiveSession session = provision(classSessionId);

        if (session.getStatus() == LiveSessionStatus.CANCELLED) {
            throw new BusinessRuleException("SESSION_CANCELLED", "This class has been cancelled.");
        }
        if (session.getStatus() == LiveSessionStatus.ENDED || session.isAttendanceComputed()) {
            throw new BusinessRuleException("CLASS_ENDED", "This class has already finished.");
        }

        ParticipantRole role = resolveRole(caller, session);

        // Hosts may open the room earlier than students, so they are set up and
        // sharing their screen by the time the class arrives.
        int earlyMinutes = role == ParticipantRole.STUDENT
                ? props.getEarlyJoinMinutes()
                : props.getEarlyJoinMinutes() * 2;
        Instant now = Instant.now();
        if (!session.joinWindowOpen(now, earlyMinutes, props.getLateJoinGraceMinutes())) {
            throw new BusinessRuleException("OUTSIDE_JOIN_WINDOW", joinWindowMessage(session, now, earlyMinutes));
        }

        roomProvisioner.ensureRoom(session);

        String identity = IDENTITY_PREFIX + caller.userId();
        Long studentId = role == ParticipantRole.STUDENT ? caller.profileId() : null;
        participantRepository.insertIfAbsent(session.getId(), identity, caller.userId(), studentId,
                caller.fullName(), role.name());

        LiveParticipant me = participantRepository.findByLiveSessionIdAndUserId(session.getId(), caller.userId())
                .orElse(null);
        RoomPermissions.Effective allowed = RoomPermissions.of(role, session, me);
        boolean canPublish = allowed.canPublishAnything();
        boolean roomAdmin = role.isRoomAdmin() || role == ParticipantRole.STAFF;

        String token = liveKit.mintJoinToken(session.getRoomName(), identity, caller.fullName(),
                participantMetadata(caller, role, studentId), allowed.sources(), roomAdmin);

        log.info("Issued {} join token for session {} to user {}", role, session.getId(), caller.userId());

        return new JoinTokenResponse(
                session.getId(), session.getClassSessionId(), session.getBatchId(),
                session.getBatchCode(), session.getCourseTitle(), session.getTopic(),
                session.getRoomName(), liveKit.wsUrl(), token, identity, caller.fullName(),
                role.name(), canPublish, roomAdmin,
                allowed.microphone(), allowed.camera(), allowed.screenShare(), session.isRecordingEnabled(),
                session.getScheduledStartAt(), session.getScheduledEndAt(),
                liveKit.expiryFrom(now));
    }

    /**
     * Decides who the caller is in this room - or refuses them.
     *
     * <p>Staff may sit in on any class. A trainer must teach this batch, as its
     * named trainer or a co-trainer. A student must hold an active place in the
     * batch, checked against batch-service at the moment of joining, so a
     * student dropped yesterday cannot rejoin today on the strength of having
     * attended last week.
     */
    private ParticipantRole resolveRole(AppPrincipal caller, LiveSession session) {
        if (caller.isStaff()) {
            return ParticipantRole.STAFF;
        }
        if (caller.isTrainer()) {
            hostAccess.requireTeaches(caller, session);
            return ParticipantRole.TRAINER;
        }
        if (caller.isStudent()) {
            hostAccess.requireEnrolled(caller, session);
            return ParticipantRole.STUDENT;
        }
        throw new ForbiddenOperationException("Your role does not take part in live classes.");
    }

    private String joinWindowMessage(LiveSession session, Instant now, int earlyMinutes) {
        Instant opens = session.getScheduledStartAt().minus(Duration.ofMinutes(earlyMinutes));
        if (now.isBefore(opens)) {
            long minutes = Math.max(1, Duration.between(now, opens).toMinutes());
            return "This class opens %d minute(s) before it starts - about %d minute(s) from now."
                    .formatted(earlyMinutes, minutes);
        }
        return "This class has finished and can no longer be joined.";
    }

    // -----------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------

    @Override
    public LiveSessionResponse get(Long liveSessionId) {
        LiveSession session = requireSession(liveSessionId);
        hostAccess.requireHostOf(session);
        return detailOf(session);
    }

    private LiveSessionResponse detailOf(LiveSession session) {
        List<LiveParticipantResponse> participants = participantRepository
                .findByLiveSessionIdOrderByDisplayNameAsc(session.getId()).stream()
                .map(LiveParticipantResponse::from)
                .toList();
        return LiveSessionResponse.detail(session, joinable(session), participants);
    }

    @Override
    @Transactional(readOnly = true)
    public LiveSessionResponse byClassSession(Long classSessionId) {
        LiveSession session = sessionRepository.findByClassSessionId(classSessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No live room has been opened for session " + classSessionId + " yet"));
        return LiveSessionResponse.summary(session, joinable(session));
    }

    @Override
    public List<LiveSessionResponse> upcomingForCaller() {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        Instant from = Instant.now().minus(Duration.ofMinutes(props.getLateJoinGraceMinutes()));

        List<LiveSession> sessions;
        if (caller.isStaff()) {
            sessions = sessionRepository.findBySessionDateOrderByStartTimeAsc(LocalDate.now(props.getZone()));
        } else if (caller.isStudent() || caller.isTrainer()) {
            List<Long> batchIds = batchClient.myBatches().stream()
                    .map(BatchClient.BatchSummary::id)
                    .toList();
            sessions = batchIds.isEmpty() ? List.of() : sessionRepository.findUpcomingForBatches(batchIds, from);
        } else {
            sessions = List.of();
        }

        return sessions.stream()
                .map(s -> LiveSessionResponse.summary(s, joinable(s)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LiveSessionResponse> pastForCaller() {
        AppPrincipal caller = SecurityUtils.requirePrincipal();

        List<LiveSession> sessions;
        if (caller.isStaff()) {
            sessions = sessionRepository.findByStatusOrderByScheduledStartAtDesc(
                    LiveSessionStatus.ENDED, org.springframework.data.domain.PageRequest.of(0, 50)).getContent();
        } else if (caller.isStudent() || caller.isTrainer()) {
            List<Long> batchIds = batchClient.myBatches().stream()
                    .map(BatchClient.BatchSummary::id)
                    .toList();
            sessions = batchIds.isEmpty() ? List.of() : sessionRepository.findPastForBatches(
                    batchIds, org.springframework.data.domain.PageRequest.of(0, 50)).getContent();
        } else {
            sessions = List.of();
        }

        return sessions.stream()
                .map(s -> LiveSessionResponse.summary(s, joinable(s)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LiveSessionResponse> forBatch(Long batchId) {
        return sessionRepository.findByBatchIdOrderByScheduledStartAtDesc(batchId).stream()
                .map(s -> LiveSessionResponse.summary(s, joinable(s)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LiveParticipantResponse> myRoomTime(Long batchId) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.studentIdOrNull() == null) {
            throw new ForbiddenOperationException("Your account is not linked to a student profile yet.");
        }
        return participantRepository.findForStudentInBatch(caller.profileId(), batchId).stream()
                .map(LiveParticipantResponse::from)
                .toList();
    }

    // -----------------------------------------------------------------
    // Running the class
    // -----------------------------------------------------------------

    @Override
    public LiveSessionResponse endClass(Long liveSessionId) {
        LiveSession session = requireSession(liveSessionId);
        hostAccess.requireHostOf(session);

        if (session.getStatus() == LiveSessionStatus.CANCELLED) {
            throw new BusinessRuleException("SESSION_CANCELLED", "This class was cancelled and has nothing to end.");
        }

        boolean settledNow = attendanceService.settle(liveSessionId, Instant.now());

        // The room is closed after the attendance transaction has committed, not
        // inside it. Closing it fires a leave webhook for everyone still inside;
        // arriving after settlement, those are recorded and otherwise ignored.
        liveKit.deleteRoomQuietly(session.getRoomName());

        if (settledNow) {
            events.audit(SERVICE_NAME, "LIVE_CLASS_ENDED", "LiveSession", liveSessionId, null,
                    Map.of("classSessionId", session.getClassSessionId()));
        }
        return detailOf(requireSession(liveSessionId));
    }

    @Override
    public void removeParticipant(Long liveSessionId, Long userId) {
        LiveSession session = requireSession(liveSessionId);
        hostAccess.requireHostOf(session);

        LiveParticipant participant = participantRepository.findByLiveSessionIdAndUserId(liveSessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("That person has not joined this class"));

        if (participant.getRole() != ParticipantRole.STUDENT) {
            throw new BusinessRuleException("CANNOT_REMOVE_HOST",
                    "Only students can be removed from a class.");
        }

        // The leave webhook that follows closes their stint, so the minutes they
        // were present still count; removal is not a retroactive absence.
        liveKit.removeParticipant(session.getRoomName(), participant.getIdentity());

        events.audit(SERVICE_NAME, "LIVE_PARTICIPANT_REMOVED", "LiveSession", liveSessionId, null,
                Map.of("userId", userId));
        log.info("Removed user {} from live session {}", userId, liveSessionId);
    }

    @Override
    public void cancelForClassSession(Long classSessionId, String reason) {
        var cancelled = transactionTemplate.execute(status ->
                sessionRepository.findByClassSessionId(classSessionId)
                        .map(session -> cancelInPlace(session, reason))
                        .orElse(null));

        if (cancelled != null && cancelled.getStatus() == LiveSessionStatus.CANCELLED) {
            liveKit.deleteRoomQuietly(cancelled.getRoomName());
        }
    }

    /**
     * Cancels unless the class already ran.
     *
     * <p>A session with published attendance happened, whatever the timetable
     * now says; turning it into CANCELLED would disown a register batch-service
     * has already written.
     */
    private LiveSession cancelInPlace(LiveSession session, String reason) {
        if (session.isAttendanceComputed()) {
            log.warn("Not cancelling live session {}: attendance was already published", session.getId());
            return session;
        }
        session.setStatus(LiveSessionStatus.CANCELLED);
        log.info("Cancelled live session {} ({})", session.getId(), reason);
        return sessionRepository.save(session);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private LiveSession newSession(Long classSessionId, Long batchId, String batchCode, String courseTitle,
                                   Long trainerId, Long trainerUserId, String topic,
                                   LocalDate date, LocalTime start, LocalTime end) {
        return LiveSession.builder()
                .classSessionId(classSessionId)
                .batchId(batchId)
                .batchCode(batchCode)
                .courseTitle(courseTitle)
                .trainerId(trainerId)
                .trainerUserId(trainerUserId)
                .roomName(ROOM_PREFIX + classSessionId)
                .topic(topic)
                .sessionDate(date)
                .startTime(start)
                .endTime(end)
                .scheduledStartAt(toInstant(date, start))
                .scheduledEndAt(toInstant(date, end))
                .status(LiveSessionStatus.SCHEDULED)
                .maxParticipants(props.getMaxParticipants())
                .recordingEnabled(props.isRecordingEnabled())
                .build();
    }

    /**
     * Inserts in its own transaction so a lost race can be recovered.
     *
     * <p>Two students clicking Join on a room nobody has opened both reach this
     * point. The loser's insert fails on the unique class-session key; because
     * that failure happened in a transaction of its own, it can simply read the
     * row the winner wrote.
     */
    private LiveSession insertOrFetch(LiveSession draft) {
        try {
            return transactionTemplate.execute(status -> sessionRepository.saveAndFlush(draft));
        } catch (DataIntegrityViolationException race) {
            return sessionRepository.findByClassSessionId(draft.getClassSessionId())
                    .orElseThrow(() -> race);
        }
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(props.getZone()).toInstant();
    }

    private boolean joinable(LiveSession session) {
        return !session.getStatus().isSettled()
                && !session.isAttendanceComputed()
                && session.joinWindowOpen(Instant.now(), props.getEarlyJoinMinutes(),
                        props.getLateJoinGraceMinutes());
    }

    private LiveSession requireSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Live session", id));
    }

    /**
     * Visible to everyone else in the room; the UI reads it to badge the
     * trainer. It confers nothing - grants are in the token, not here.
     */
    private String participantMetadata(AppPrincipal caller, ParticipantRole role, Long studentId) {
        return toJson(studentId == null
                ? Map.of("userId", caller.userId(), "role", role.name())
                : Map.of("userId", caller.userId(), "role", role.name(), "studentId", studentId));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
