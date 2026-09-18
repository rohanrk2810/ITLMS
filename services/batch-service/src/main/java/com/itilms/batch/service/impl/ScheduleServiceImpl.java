package com.itilms.batch.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.batch.dto.request.CreateSessionRequest;
import com.itilms.batch.dto.request.GenerateScheduleRequest;
import com.itilms.batch.dto.response.SessionResponse;
import com.itilms.batch.entity.Batch;
import com.itilms.batch.entity.BatchMode;
import com.itilms.batch.entity.ClassSession;
import com.itilms.batch.entity.SessionStatus;
import com.itilms.batch.repository.BatchRepository;
import com.itilms.batch.repository.ClassSessionRepository;
import com.itilms.batch.repository.EnrollmentRepository;
import com.itilms.batch.service.BatchService;
import com.itilms.batch.service.ScheduleService;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.SessionCancelledEvent;
import com.itilms.common.event.SessionScheduledEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private static final String SERVICE_NAME = "batch-service";
    /** Guards against a typo in the date range creating thousands of rows. */
    private static final int MAX_GENERATED_SESSIONS = 400;

    private final ClassSessionRepository sessionRepository;
    private final BatchRepository batchRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final BatchService batchService;
    private final EventPublisher events;

    // -----------------------------------------------------------------
    // Create / update
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        Batch batch = requireBatch(request.batchId());
        requireTrainerOwnsBatch(batch.getId());

        LocalTime start = request.startTime() != null ? request.startTime() : batch.getStartTime();
        LocalTime end = request.endTime() != null ? request.endTime() : batch.getEndTime();
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("A session's end time must be after its start time");
        }

        if (sessionRepository.existsByBatchIdAndSessionDateAndStartTime(
                batch.getId(), request.sessionDate(), start)) {
            throw new BusinessRuleException(
                    "This batch already has a session on %s at %s".formatted(request.sessionDate(), start));
        }

        BatchMode mode = request.mode() == null || request.mode().isBlank()
                ? batch.getMode()
                : parseMode(request.mode());

        ClassSession session = sessionRepository.save(ClassSession.builder()
                .batchId(batch.getId())
                .trainerId(request.trainerId() != null ? request.trainerId() : batch.getTrainerId())
                .trainerUserId(batch.getTrainerUserId())
                .sessionDate(request.sessionDate())
                .startTime(start)
                .endTime(end)
                .topic(trim(request.topic()))
                .mode(mode)
                .meetingUrl(mode.needsLiveRoom() ? trim(request.meetingUrl()) : null)
                .room(trim(request.room()))
                .status(SessionStatus.SCHEDULED)
                .build());

        publishScheduled(session, batch, false);
        notifyBatch(batch, session, false);

        events.audit(SERVICE_NAME, "SESSION_CREATED", "ClassSession", session.getId(), null,
                Map.of("batchId", batch.getId(), "date", request.sessionDate().toString()));

        return toResponse(session, batch);
    }

    @Override
    @Transactional
    public SessionResponse updateSession(Long sessionId, CreateSessionRequest request) {
        ClassSession session = requireSession(sessionId);
        Batch batch = requireBatch(session.getBatchId());
        requireTrainerOwnsBatch(batch.getId());

        LocalDate previousDate = session.getSessionDate();
        LocalTime previousStart = session.getStartTime();

        LocalTime start = request.startTime() != null ? request.startTime() : session.getStartTime();
        LocalTime end = request.endTime() != null ? request.endTime() : session.getEndTime();
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("A session's end time must be after its start time");
        }

        boolean moved = !previousDate.equals(request.sessionDate()) || !previousStart.equals(start);
        if (moved && sessionRepository.existsByBatchIdAndSessionDateAndStartTime(
                batch.getId(), request.sessionDate(), start)) {
            throw new BusinessRuleException(
                    "This batch already has a session on %s at %s".formatted(request.sessionDate(), start));
        }

        BatchMode mode = request.mode() == null || request.mode().isBlank()
                ? session.getMode()
                : parseMode(request.mode());

        session.setSessionDate(request.sessionDate());
        session.setStartTime(start);
        session.setEndTime(end);
        session.setTopic(trim(request.topic()));
        session.setMode(mode);
        session.setMeetingUrl(mode.needsLiveRoom() ? trim(request.meetingUrl()) : null);
        session.setRoom(trim(request.room()));
        if (request.trainerId() != null) {
            session.setTrainerId(request.trainerId());
        }
        if (moved) {
            session.setStatus(SessionStatus.RESCHEDULED);
        }
        sessionRepository.save(session);

        if (moved) {
            // Doc S16: affected students and the trainer are told when a class
            // moves. Silence here is how a room full of people turns up on the
            // wrong day.
            publishScheduled(session, batch, true);
            notifyBatch(batch, session, true);

            events.audit(SERVICE_NAME, "SESSION_RESCHEDULED", "ClassSession", sessionId,
                    Map.of("date", previousDate.toString(), "startTime", previousStart.toString()),
                    Map.of("date", session.getSessionDate().toString(),
                            "startTime", session.getStartTime().toString()));
        }

        return toResponse(session, batch);
    }

    @Override
    @Transactional
    public SessionResponse cancelSession(Long sessionId, String reason) {
        ClassSession session = requireSession(sessionId);
        Batch batch = requireBatch(session.getBatchId());
        requireTrainerOwnsBatch(batch.getId());

        if (session.isAttendanceMarked()) {
            // Cancelling a class people already attended would erase the fact
            // that it happened and skew everyone's percentage.
            throw new BusinessRuleException(
                    "Attendance has already been recorded for this session, so it cannot be cancelled.");
        }

        session.cancel(reason);
        sessionRepository.save(session);

        // liveclass-service listens for this. A cancelled online class whose
        // room stays open is worse than no room at all: students file in and
        // sit waiting for a trainer who was told the class was off.
        events.publishAfterCommit(KafkaTopics.SESSION_CANCELLED,
                new SessionCancelledEvent(
                        DomainEvent.newId(), Instant.now(),
                        session.getId(), batch.getId(), batch.getBatchCode(),
                        session.getSessionDate(), session.getStartTime(), reason));

        notifyBatchUsers(batch.getId(), "SCHEDULE_CHANGE",
                "Class cancelled: " + batch.getBatchCode(),
                "The class on %s at %s has been cancelled.%s".formatted(
                        session.getSessionDate(), session.getStartTime(),
                        reason == null || reason.isBlank() ? "" : " Reason: " + reason),
                "/student/timetable");

        events.audit(SERVICE_NAME, "SESSION_CANCELLED", "ClassSession", sessionId, null,
                Map.of("reason", String.valueOf(reason)));

        log.info("Cancelled session {} of batch {}: {}", sessionId, batch.getId(), reason);
        return toResponse(session, batch);
    }

    /**
     * Expands the batch's weekly pattern into sessions.
     *
     * <p>Existing slots are skipped rather than treated as errors, which makes
     * the operation safe to repeat: extending a term by four weeks is the same
     * call with a later end date.
     */
    @Override
    @Transactional
    public List<SessionResponse> generateSchedule(Long batchId, GenerateScheduleRequest request) {
        Batch batch = requireBatch(batchId);
        requireTrainerOwnsBatch(batchId);

        if (request.to().isBefore(request.from())) {
            throw new BusinessRuleException("The end date cannot be before the start date");
        }

        Set<java.time.DayOfWeek> meetingDays = batch.meetingDays();
        if (meetingDays.isEmpty()) {
            throw new BusinessRuleException(
                    "This batch has no class days set, so a schedule cannot be generated.");
        }

        Set<LocalDate> excluded = new HashSet<>(request.excludeDatesOrEmpty());
        List<ClassSession> created = new ArrayList<>();
        int skipped = 0;

        for (LocalDate date = request.from(); !date.isAfter(request.to()); date = date.plusDays(1)) {
            if (!meetingDays.contains(date.getDayOfWeek()) || excluded.contains(date)) {
                continue;
            }
            if (sessionRepository.existsByBatchIdAndSessionDateAndStartTime(
                    batchId, date, batch.getStartTime())) {
                skipped++;
                continue;
            }
            if (created.size() >= MAX_GENERATED_SESSIONS) {
                throw new BusinessRuleException(
                        ("This range would create more than %d sessions, which is almost certainly a "
                                + "mistyped date. Generate a shorter period.")
                                .formatted(MAX_GENERATED_SESSIONS));
            }

            created.add(ClassSession.builder()
                    .batchId(batchId)
                    .trainerId(batch.getTrainerId())
                    .trainerUserId(batch.getTrainerUserId())
                    .sessionDate(date)
                    .startTime(batch.getStartTime())
                    .endTime(batch.getEndTime())
                    .topic(trim(request.defaultTopic()))
                    .mode(batch.getMode())
                    .room(batch.getClassroom())
                    .status(SessionStatus.SCHEDULED)
                    .build());
        }

        if (created.isEmpty()) {
            throw new BusinessRuleException(
                    "No sessions were created. Every date in this range is either outside the batch's "
                            + "class days, excluded, or already scheduled.");
        }

        sessionRepository.saveAll(created);

        // One event per session so liveclass-service can provision rooms, but
        // deliberately no per-session notification: telling a student about
        // sixty new classes individually would bury everything else.
        created.forEach(session -> publishScheduled(session, batch, false));

        notifyBatchUsers(batchId, "SCHEDULE_CHANGE",
                "Your timetable is ready",
                "%d classes have been scheduled for %s between %s and %s."
                        .formatted(created.size(), batch.getBatchCode(), request.from(), request.to()),
                "/student/timetable");

        events.audit(SERVICE_NAME, "SCHEDULE_GENERATED", "Batch", batchId, null,
                Map.of("created", created.size(), "skipped", skipped,
                        "from", request.from().toString(), "to", request.to().toString()));

        log.info("Generated {} session(s) for batch {} ({} already existed)",
                created.size(), batchId, skipped);

        return created.stream().map(session -> toResponse(session, batch)).toList();
    }

    // -----------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SessionResponse get(Long sessionId) {
        ClassSession session = requireSession(sessionId);
        return toResponse(session, requireBatch(session.getBatchId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> sessionsForBatch(Long batchId) {
        Batch batch = requireBatch(batchId);
        return sessionRepository.findByBatchIdOrderBySessionDateAscStartTimeAsc(batchId).stream()
                .map(session -> toResponse(session, batch))
                .toList();
    }

    /**
     * Scopes the timetable to what the caller is entitled to see.
     *
     * <p>Doc S6.7: "students see only sessions belonging to their batch". The
     * batch list is derived from the caller's own enrolments, so passing another
     * batch's id simply yields nothing.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> timetableForCaller(LocalDate from, LocalDate to, Long batchIdFilter) {
        AppPrincipal principal = SecurityUtils.requirePrincipal();

        LocalDate start = from != null ? from : LocalDate.now().minusDays(7);
        LocalDate end = to != null ? to : start.plusDays(30);
        if (end.isBefore(start)) {
            throw new BusinessRuleException("The end date cannot be before the start date");
        }

        List<ClassSession> sessions;

        if (principal.isStudent()) {
            List<Long> batchIds = batchService.activeBatchIdsForStudent(principal.profileId());
            if (batchIds.isEmpty()) {
                return List.of();
            }
            if (batchIdFilter != null && !batchIds.contains(batchIdFilter)) {
                throw new ForbiddenOperationException("You are not enrolled in that batch");
            }
            sessions = sessionRepository.findTimetable(
                    batchIdFilter != null ? List.of(batchIdFilter) : batchIds, start, end);

        } else if (principal.isTrainer()) {
            List<Long> batchIds = batchService.batchIdsForTrainer(principal.profileId());
            if (batchIds.isEmpty()) {
                return List.of();
            }
            if (batchIdFilter != null && !batchIds.contains(batchIdFilter)) {
                throw new ForbiddenOperationException("That batch is not assigned to you");
            }
            sessions = sessionRepository.findTimetable(
                    batchIdFilter != null ? List.of(batchIdFilter) : batchIds, start, end);

        } else {
            List<Long> batchIds = batchIdFilter != null
                    ? List.of(batchIdFilter)
                    : batchRepository.findActiveBatchIds();
            sessions = batchIds.isEmpty() ? List.of()
                    : sessionRepository.findTimetable(batchIds, start, end);
        }

        return decorate(sessions);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> today() {
        AppPrincipal principal = SecurityUtils.requirePrincipal();
        Long trainerId = principal.isTrainer() ? principal.profileId() : null;

        List<ClassSession> sessions = sessionRepository.findTodaysSessions(LocalDate.now(), trainerId);

        if (principal.isStudent()) {
            List<Long> batchIds = batchService.activeBatchIdsForStudent(principal.profileId());
            sessions = sessions.stream()
                    .filter(session -> batchIds.contains(session.getBatchId()))
                    .toList();
        }
        return decorate(sessions);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> pendingAttendance(int lookbackDays) {
        AppPrincipal principal = SecurityUtils.requirePrincipal();
        Long trainerId = principal.isTrainer() ? principal.profileId() : null;

        int days = Math.max(1, Math.min(lookbackDays, 90));
        List<ClassSession> sessions = sessionRepository.findPendingAttendance(
                LocalDate.now().minusDays(days), LocalDate.now(), trainerId);

        // A class that has not finished yet is not "pending" - the trainer is
        // probably still teaching it.
        return decorate(sessions.stream().filter(ClassSession::hasFinished).toList());
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private List<SessionResponse> decorate(List<ClassSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<Long> batchIds = sessions.stream().map(ClassSession::getBatchId).distinct().toList();
        Map<Long, Batch> batches = batchRepository.findByIdIn(batchIds).stream()
                .collect(java.util.stream.Collectors.toMap(Batch::getId, b -> b));

        return sessions.stream()
                .map(session -> toResponse(session, batches.get(session.getBatchId())))
                .toList();
    }

    private SessionResponse toResponse(ClassSession session, Batch batch) {
        if (batch == null) {
            return SessionResponse.from(session);
        }
        return SessionResponse.from(session, batch.getBatchCode(),
                batch.getCourseTitle(), batch.getTrainerName());
    }

    private void publishScheduled(ClassSession session, Batch batch, boolean rescheduled) {
        events.publishAfterCommit(
                rescheduled ? KafkaTopics.SESSION_RESCHEDULED : KafkaTopics.SESSION_SCHEDULED,
                new SessionScheduledEvent(
                        DomainEvent.newId(), Instant.now(),
                        session.getId(), batch.getId(), batch.getBatchCode(),
                        session.getTrainerId(), session.getTrainerUserId(),
                        session.getSessionDate(), session.getStartTime(), session.getEndTime(),
                        session.getTopic(), session.getMode().name(), rescheduled));
    }

    private void notifyBatch(Batch batch, ClassSession session, boolean rescheduled) {
        String title = rescheduled
                ? "Class moved: " + batch.getBatchCode()
                : "New class scheduled: " + batch.getBatchCode();
        String message = "%s at %s%s".formatted(
                session.getSessionDate(), session.getStartTime(),
                session.getTopic() == null ? "" : " - " + session.getTopic());
        notifyBatchUsers(batch.getId(), "SCHEDULE_CHANGE", title, message, "/student/timetable");
    }

    private void notifyBatchUsers(Long batchId, String type, String title,
                                  String message, String actionUrl) {
        List<Long> userIds = enrollmentRepository.findActiveUserIds(batchId);
        events.notifyUsers(userIds, type, title, message, actionUrl);
    }

    private void requireTrainerOwnsBatch(Long batchId) {
        AppPrincipal principal = SecurityUtils.requirePrincipal();
        if (!principal.isTrainer()) {
            return;   // staff may schedule any batch
        }
        if (!batchService.batchIdsForTrainer(principal.profileId()).contains(batchId)) {
            throw new ForbiddenOperationException("You may only change the timetable of your own batches");
        }
    }

    private Batch requireBatch(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));
    }

    private ClassSession requireSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
    }

    private BatchMode parseMode(String value) {
        try {
            return BatchMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessRuleException("Mode must be ONLINE, OFFLINE or HYBRID");
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
