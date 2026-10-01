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
        AppPrincipal admin = requireAdmin();
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
        eventRepository.save(MonitoringEvent.builder()
                .liveSessionId(session.getId())
                .studentId(caller.profileId())
                .studentUserId(caller.userId())
                .studentName(participant.getDisplayName())
                .eventType(request.type())
                .severity(MonitoringRules.severity(request.type(), request.durationSeconds()))
                .occurredAt(now)
                .offsetSeconds(QuestionGrader.offsetSeconds(
                        session.getStartedAt() != null ? session.getStartedAt() : session.getScheduledStartAt(), now))
                .durationSeconds(request.durationSeconds())
                .detail(request.detail())
                .build());
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
                e.showWarning(), e.warningMessage(), e.logEvents(), e.source());
    }
}
