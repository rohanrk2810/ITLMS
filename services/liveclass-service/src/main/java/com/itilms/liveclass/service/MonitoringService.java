package com.itilms.liveclass.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.liveclass.client.BatchClient;
import com.itilms.liveclass.dto.MonitoringDtos.EffectiveResponse;
import com.itilms.liveclass.dto.MonitoringDtos.EventRequest;
import com.itilms.liveclass.dto.MonitoringDtos.EventResponse;
import com.itilms.liveclass.dto.MonitoringDtos.SettingRequest;
import com.itilms.liveclass.dto.MonitoringDtos.SettingResponse;
import com.itilms.liveclass.dto.MonitoringDtos.StudentReport;
import com.itilms.liveclass.livekit.LiveKitGateway;
import com.itilms.liveclass.entity.LiveParticipant;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.LiveSessionStatus;
import com.itilms.liveclass.entity.MonitoringEvent;
import com.itilms.liveclass.entity.MonitoringSetting;
import com.itilms.liveclass.repository.LiveParticipantRepository;
import com.itilms.liveclass.repository.LiveSessionRepository;
import com.itilms.liveclass.repository.MonitoringEventRepository;
import com.itilms.liveclass.repository.MonitoringSettingRepository;
import com.itilms.liveclass.service.MonitoringRules.Effective;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Camera-based student monitoring in live classes.
 *
 * <p>Only an ADMIN decides whether it is on, and at what level. The student's browser does the face check and
 * reports what it saw; this service refuses reports for a class where monitoring is off, from anyone who is not
 * a student who joined that class, and past a per-student cap. Trainers and staff read the events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringService {

    private static final String SERVICE_NAME = "liveclass-service";
    /** A student cannot flood a class's log; a long class with a flickering camera stays well under this. */
    static final int MAX_EVENTS_PER_STUDENT = 300;

    private final MonitoringSettingRepository settings;
    private final MonitoringEventRepository eventRepository;
    private final LiveSessionRepository sessions;
    private final LiveParticipantRepository participants;
    private final BatchClient batchClient;
    private final HostAccess hostAccess;
    private final EventPublisher events;
    private final LiveKitGateway liveKit;

    /** A batch's course does not change, so it is asked for once. */
    private final Map<Long, Long> courseOfBatch = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------
    // Settings (ADMIN)
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SettingResponse> listSettings() {
        requireAdmin();
        return settings.findAllByOrderByScopeTypeAscScopeIdAsc().stream().map(SettingResponse::from).toList();
    }

    @Transactional
    public SettingResponse save(SettingRequest request) {
        AppPrincipal admin = requireSettingsEditor(request);
        long scopeId = request.scopeType() == MonitoringSetting.Scope.INSTITUTE ? 0
                : request.scopeId() == null ? -1 : request.scopeId();
        if (scopeId < 0 || (request.scopeType() != MonitoringSetting.Scope.INSTITUTE && scopeId == 0)) {
            throw new BusinessRuleException("Choose which " + request.scopeType().name().toLowerCase()
                    + " this setting is for.");
        }

        MonitoringSetting row = settings.findByScopeTypeAndScopeId(request.scopeType(), scopeId)
                .orElseGet(() -> MonitoringSetting.builder().scopeType(request.scopeType()).scopeId(scopeId).build());
        row.setEnabled(request.enabled());
        row.setFaceVisibility(request.faceVisibility() == null || request.faceVisibility());
        row.setCameraRequired(request.cameraRequired() != null && request.cameraRequired());
        row.setMicrophoneRequired(request.microphoneRequired() != null && request.microphoneRequired());
        row.setWarningAfterSeconds(request.warningAfterSeconds() == null ? 10 : request.warningAfterSeconds());
        row.setShowWarning(request.showWarning() == null || request.showWarning());
        row.setWarningMessage(request.warningMessage() == null || request.warningMessage().isBlank()
                ? null : request.warningMessage().trim());
        row.setLogEvents(request.logEvents() == null || request.logEvents());
        row.setTabSwitchCheck(Boolean.TRUE.equals(request.tabSwitchCheck()));
        row.setAttendancePenalty(Boolean.TRUE.equals(request.attendancePenalty()));
        row.setStrictSeverity(Boolean.TRUE.equals(request.strictSeverity()));
        row.setAutoRemove(Boolean.TRUE.equals(request.autoRemove()));
        row.setAutoRemoveAfterCritical(
                request.autoRemoveAfterCritical() == null ? 3 : request.autoRemoveAfterCritical());
        row.setAlertTrainer(Boolean.TRUE.equals(request.alertTrainer()));
        row.setUpdatedBy(admin.userId());
        row = settings.save(row);

        events.audit(SERVICE_NAME, "MONITORING_SETTING_CHANGED", "MonitoringSetting", row.getId(), null,
                Map.of("scope", row.getScopeType().name(), "scopeId", row.getScopeId(), "enabled", row.isEnabled()));
        log.info("Monitoring {} at {} {} by admin {}", row.isEnabled() ? "ON" : "OFF", row.getScopeType(),
                row.getScopeId(), admin.userId());
        return SettingResponse.from(row);
    }

    /** Removing a setting makes that level inherit from the one above it. */
    @Transactional
    public void remove(Long id) {
        AppPrincipal admin = requireAdmin();
        MonitoringSetting row = settings.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Monitoring setting", id));
        settings.delete(row);
        events.audit(SERVICE_NAME, "MONITORING_SETTING_REMOVED", "MonitoringSetting", id, null,
                Map.of("scope", row.getScopeType().name(), "scopeId", row.getScopeId(), "by", admin.userId()));
    }

    // -----------------------------------------------------------------
    // What applies to a class
    // -----------------------------------------------------------------

    /** For a student's browser (to start the check) or a host's screen. Nobody outside the class gets it. */
    @Transactional(readOnly = true)
    public EffectiveResponse effectiveForClass(Long classSessionId) {
        LiveSession session = requireByClassSession(classSessionId);
        hostAccess.requireHostOrEnrolled(session);
        return toResponse(effective(session));
    }

    Effective effective(LiveSession session) {
        List<MonitoringSetting> found = new ArrayList<>(4);
        settings.findByScopeTypeAndScopeId(MonitoringSetting.Scope.SESSION, session.getClassSessionId())
                .ifPresent(found::add);
        settings.findByScopeTypeAndScopeId(MonitoringSetting.Scope.BATCH, session.getBatchId()).ifPresent(found::add);
        Long courseId = courseOf(session.getBatchId());
        if (courseId != null) {
            settings.findByScopeTypeAndScopeId(MonitoringSetting.Scope.COURSE, courseId).ifPresent(found::add);
        }
        settings.findByScopeTypeAndScopeId(MonitoringSetting.Scope.INSTITUTE, 0L).ifPresent(found::add);
        return MonitoringRules.resolve(found);
    }

    private Long courseOf(Long batchId) {
        Long cached = courseOfBatch.get(batchId);
        if (cached != null) {
            return cached;
        }
        var batch = batchClient.batch(batchId);
        if (batch == null || batch.courseId() == null) {
            // Unknown right now: skip the course level rather than guess. The other levels still apply.
            return null;
        }
        courseOfBatch.put(batchId, batch.courseId());
        return batch.courseId();
    }

    // -----------------------------------------------------------------
    // Events
    // -----------------------------------------------------------------

    /** A student's browser reports what its camera check saw. Quietly ignored where monitoring is off. */
    @Transactional
    public void record(Long classSessionId, EventRequest request) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (!caller.isStudent() || caller.profileId() == null) {
            throw new ForbiddenOperationException("Only a student reports monitoring events.");
        }
        LiveSession session = requireByClassSession(classSessionId);
        if (session.getStatus() != LiveSessionStatus.LIVE) {
            return;
        }
        // Having joined this class (a token is only minted for an enrolled student) is the proof of belonging.
        LiveParticipant participant = participants.findByLiveSessionIdAndUserId(session.getId(), caller.userId())
                .orElseThrow(() -> new ForbiddenOperationException("You have not joined this class."));

        Effective effective = effective(session);
        if (!MonitoringRules.accepts(effective, request.type())) {
            return;
        }
        if (eventRepository.countByLiveSessionIdAndStudentId(session.getId(), caller.profileId())
                >= MAX_EVENTS_PER_STUDENT) {
            return;
        }

        Instant now = Instant.now();
        MonitoringEvent.Severity severity =
                MonitoringRules.severity(request.type(), request.durationSeconds(), effective.strictSeverity());
        eventRepository.save(saveable(session, caller, participant, request.type(), severity, request.durationSeconds(),
                request.detail(), now));

        if (effective.autoRemove() && severity == MonitoringEvent.Severity.CRITICAL) {
            removeIfOverLimit(session, caller, participant, effective, now);
        }
    }

    private MonitoringEvent saveable(LiveSession session, AppPrincipal student, LiveParticipant participant, MonitoringEvent.Type type,
                                     MonitoringEvent.Severity severity, Integer durationSeconds, String detail,
                                     Instant now) {
        return MonitoringEvent.builder()
                .liveSessionId(session.getId())
                .studentId(student.profileId())
                .studentUserId(student.userId())
                .studentName(participant.getDisplayName())
                .eventType(type)
                .severity(severity)
                .occurredAt(now)
                .offsetSeconds(QuestionGrader.offsetSeconds(
                        session.getStartedAt() != null ? session.getStartedAt() : session.getScheduledStartAt(), now))
                .durationSeconds(durationSeconds)
                .detail(detail)
                .build();
    }

    /**
     * Removes a student from the room once their CRITICAL notes reach the limit (and again at each further multiple,
     * so a student who rejoins gets a fresh run). Removal never fails the report: if the media server cannot be
     * reached the notes are still kept and the trainer can act.
     */
    private void removeIfOverLimit(LiveSession session, AppPrincipal student, LiveParticipant participant,
                                   Effective effective, Instant now) {
        long critical = eventRepository.countByLiveSessionIdAndStudentIdAndSeverity(
                session.getId(), student.profileId(), MonitoringEvent.Severity.CRITICAL);
        int limit = Math.max(1, effective.autoRemoveAfterCritical());
        if (critical == 0 || critical % limit != 0) {
            return;
        }
        try {
            liveKit.removeParticipant(session.getRoomName(), participant.getIdentity());
        } catch (RuntimeException ex) {
            log.warn("Could not remove user {} from live session {} after {} critical notes", student.userId(),
                    session.getId(), critical, ex);
            return;
        }
        eventRepository.save(saveable(session, student, participant, MonitoringEvent.Type.REMOVED_BY_MONITORING,
                MonitoringEvent.Severity.CRITICAL, null, "Removed after " + critical + " critical notes", now));
        events.audit(SERVICE_NAME, "MONITORING_AUTO_REMOVED", "LiveSession", session.getId(), null,
                Map.of("userId", student.userId(), "criticalNotes", critical));
        log.info("Removed user {} from live session {} by monitoring ({} critical notes)", student.userId(),
                session.getId(), critical);
    }

    /**
     * Seconds to take off one student's attended time for this class, when the attendance-penalty switch is on.
     * Called when attendance is settled; zero when the switch is off or nothing was noted.
     */
    @Transactional(readOnly = true)
    public int penaltySeconds(LiveSession session, Long studentId) {
        Effective effective = effective(session);
        if (!effective.enabled() || !effective.attendancePenalty() || studentId == null) {
            return 0;
        }
        return MonitoringRules.penaltySeconds(
                eventRepository.findByLiveSessionIdAndStudentIdOrderByOccurredAtAscIdAsc(session.getId(), studentId),
                effective);
    }

    /** One line per student: what monitoring noted, for the class trainer and staff. */
    @Transactional(readOnly = true)
    public List<StudentReport> reportOf(Long classSessionId) {
        LiveSession session = requireByClassSession(classSessionId);
        hostAccess.requireHostOf(session);
        Effective effective = effective(session);

        Map<Long, List<MonitoringEvent>> byStudent = new java.util.LinkedHashMap<>();
        for (MonitoringEvent e : eventRepository.findByLiveSessionIdOrderByOccurredAtAscIdAsc(session.getId())) {
            byStudent.computeIfAbsent(e.getStudentId(), k -> new ArrayList<>()).add(e);
        }

        List<StudentReport> report = new ArrayList<>();
        byStudent.forEach((studentId, list) -> {
            int absences = 0;
            int absentSeconds = 0;
            int tabSwitches = 0;
            int tabSeconds = 0;
            int multiple = 0;
            int cameraProblems = 0;
            int warnings = 0;
            int critical = 0;
            boolean removed = false;
            for (MonitoringEvent e : list) {
                int duration = e.getDurationSeconds() == null ? 0 : e.getDurationSeconds();
                switch (e.getEventType()) {
                    case FACE_RESTORED -> {
                        absences++;
                        absentSeconds += duration;
                    }
                    case TAB_AWAY -> {
                        tabSwitches++;
                        tabSeconds += duration;
                    }
                    case MULTIPLE_FACES -> multiple++;
                    case CAMERA_DISABLED, CAMERA_PERMISSION_DENIED -> cameraProblems++;
                    case REMOVED_BY_MONITORING -> removed = true;
                    default -> { }
                }
                if (e.getSeverity() == MonitoringEvent.Severity.WARNING) {
                    warnings++;
                } else if (e.getSeverity() == MonitoringEvent.Severity.CRITICAL) {
                    critical++;
                }
            }
            // The penalty is shown by what it would be, so a trainer sees the effect even before attendance is settled.
            int penalty = MonitoringRules.penaltySeconds(list, effective);
            report.add(new StudentReport(studentId, list.get(0).getStudentName(), absences, absentSeconds,
                    tabSwitches, tabSeconds, multiple, cameraProblems, warnings, critical, removed, penalty));
        });
        report.sort(java.util.Comparator.comparingInt(StudentReport::critical).reversed()
                .thenComparing(java.util.Comparator.comparingInt(StudentReport::warnings).reversed()));
        return report;
    }

    /** The class trainer and staff read what was noted. */
    @Transactional(readOnly = true)
    public List<EventResponse> eventsOf(Long classSessionId) {
        LiveSession session = requireByClassSession(classSessionId);
        hostAccess.requireHostOf(session);
        return eventRepository.findByLiveSessionIdOrderByOccurredAtAscIdAsc(session.getId()).stream()
                .map(e -> EventResponse.from(e, QuestionGrader.label(e.getOffsetSeconds())))
                .toList();
    }

    // -----------------------------------------------------------------

    /**
     * An admin may set any level. A class's own trainer (or staff) may set only that one class - never a batch,
     * course or the institute - so a trainer decides for the lecture they are teaching and nothing wider.
     */
    private AppPrincipal requireSettingsEditor(SettingRequest request) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.isAdmin()) {
            return caller;
        }
        if (request.scopeType() != MonitoringSetting.Scope.SESSION || request.scopeId() == null) {
            throw new ForbiddenOperationException("Only an administrator can change monitoring settings for more than one class.");
        }
        hostAccess.requireHostOf(requireByClassSession(request.scopeId()));
        return caller;
    }

    private AppPrincipal requireAdmin() {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (!caller.isAdmin()) {
            throw new ForbiddenOperationException("Only an administrator can change monitoring settings.");
        }
        return caller;
    }

    private LiveSession requireByClassSession(Long classSessionId) {
        return sessions.findByClassSessionId(classSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Live class for session", classSessionId));
    }

    private static EffectiveResponse toResponse(Effective e) {
        return new EffectiveResponse(e.enabled(), e.faceVisibility(), e.cameraRequired(), e.microphoneRequired(),
                e.warningAfterSeconds(),
                e.showWarning(), e.warningMessage(), e.logEvents(), e.source(),
                e.tabSwitchCheck(), e.attendancePenalty(), e.strictSeverity(), e.autoRemove(),
                e.autoRemoveAfterCritical(), e.alertTrainer());
    }
}
