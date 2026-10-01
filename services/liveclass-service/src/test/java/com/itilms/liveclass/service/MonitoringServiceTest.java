package com.itilms.liveclass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.liveclass.client.BatchClient;
import com.itilms.liveclass.dto.MonitoringDtos.EventRequest;
import com.itilms.liveclass.dto.MonitoringDtos.SettingRequest;
import com.itilms.liveclass.entity.LiveParticipant;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.LiveSessionStatus;
import com.itilms.liveclass.entity.MonitoringEvent;
import com.itilms.liveclass.entity.MonitoringSetting;
import com.itilms.liveclass.entity.MonitoringSetting.Scope;
import com.itilms.liveclass.repository.LiveParticipantRepository;
import com.itilms.liveclass.repository.LiveSessionRepository;
import com.itilms.liveclass.repository.MonitoringEventRepository;
import com.itilms.liveclass.repository.MonitoringSettingRepository;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {

    private static final long CLASS_SESSION = 11L;
    private static final long LIVE_SESSION = 21L;
    private static final long BATCH = 31L;
    private static final long COURSE = 41L;

    @Mock MonitoringSettingRepository settings;
    @Mock MonitoringEventRepository eventRepository;
    @Mock LiveSessionRepository sessions;
    @Mock LiveParticipantRepository participants;
    @Mock BatchClient batchClient;
    @Mock HostAccess hostAccess;
    @Mock EventPublisher events;

    MonitoringService service;
    LiveSession session;

    @BeforeEach
    void setUp() {
        service = new MonitoringService(settings, eventRepository, sessions, participants, batchClient, hostAccess,
                events);
        session = new LiveSession();
        session.setId(LIVE_SESSION);
        session.setClassSessionId(CLASS_SESSION);
        session.setBatchId(BATCH);
        session.setStatus(LiveSessionStatus.LIVE);
        session.setStartedAt(Instant.now().minusSeconds(120));
        lenient().when(sessions.findByClassSessionId(CLASS_SESSION)).thenReturn(Optional.of(session));
        lenient().when(batchClient.batch(BATCH)).thenReturn(new BatchClient.BatchInfo(BATCH, COURSE));
        lenient().when(settings.findByScopeTypeAndScopeId(any(), anyLong())).thenReturn(Optional.empty());
        lenient().when(settings.save(any(MonitoringSetting.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(String role, Long profileId) {
        AppPrincipal p = new AppPrincipal(7L, "u@x", "User", role, profileId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.authorities()));
    }

    private static MonitoringSetting setting(Scope scope, long id, boolean enabled) {
        return MonitoringSetting.builder().scopeType(scope).scopeId(id).enabled(enabled).faceVisibility(true)
                .warningAfterSeconds(10).showWarning(true).logEvents(true).build();
    }

    private static SettingRequest on(Scope scope, Long id) {
        return new SettingRequest(scope, id, true, null, null, null, null, null, null);
    }

    // ------------------------------------------------------------------ resolution

    @Test
    void nothingIsMonitoredWhenNoSettingExists() {
        assertThat(MonitoringRules.resolve(List.of()).enabled()).isFalse();
    }

    @Test
    void theMostSpecificLevelWinsOverTheGeneralOne() {
        when(settings.findByScopeTypeAndScopeId(Scope.INSTITUTE, 0L))
                .thenReturn(Optional.of(setting(Scope.INSTITUTE, 0, true)));
        when(settings.findByScopeTypeAndScopeId(Scope.BATCH, BATCH))
                .thenReturn(Optional.of(setting(Scope.BATCH, BATCH, false)));

        var effective = service.effective(session);

        assertThat(effective.enabled()).as("batch OFF beats institute ON").isFalse();
        assertThat(effective.source()).isEqualTo("BATCH");
    }

    @Test
    void theClassOverridesItsBatch() {
        when(settings.findByScopeTypeAndScopeId(Scope.SESSION, CLASS_SESSION))
                .thenReturn(Optional.of(setting(Scope.SESSION, CLASS_SESSION, true)));
        when(settings.findByScopeTypeAndScopeId(Scope.BATCH, BATCH))
                .thenReturn(Optional.of(setting(Scope.BATCH, BATCH, false)));

        assertThat(service.effective(session).enabled()).isTrue();
    }

    @Test
    void aCourseLevelSettingAppliesThroughTheBatchsCourse() {
        when(settings.findByScopeTypeAndScopeId(Scope.COURSE, COURSE))
                .thenReturn(Optional.of(setting(Scope.COURSE, COURSE, true)));

        assertThat(service.effective(session).source()).isEqualTo("COURSE");
    }

    @Test
    void whenTheCourseCannotBeLearnedTheOtherLevelsStillApply() {
        when(batchClient.batch(BATCH)).thenReturn(null);
        when(settings.findByScopeTypeAndScopeId(Scope.INSTITUTE, 0L))
                .thenReturn(Optional.of(setting(Scope.INSTITUTE, 0, true)));

        assertThat(service.effective(session).enabled()).isTrue();
    }

    // ------------------------------------------------------------------ admin only

    @Test
    void onlyAnAdminChangesSettings() {
        for (String role : List.of("COORDINATOR", "TRAINER", "STUDENT")) {
            signInAs(role, 3L);
            assertThatThrownBy(() -> service.save(on(Scope.INSTITUTE, null)))
                    .isInstanceOf(ForbiddenOperationException.class);
            assertThatThrownBy(() -> service.listSettings()).isInstanceOf(ForbiddenOperationException.class);
            assertThatThrownBy(() -> service.remove(1L)).isInstanceOf(ForbiddenOperationException.class);
        }
        verify(settings, never()).save(any());
    }

    @Test
    void adminSavesAnInstituteWideSettingWithSaneDefaults() {
        signInAs("ADMIN", null);

        var saved = service.save(on(Scope.INSTITUTE, 99L));

        assertThat(saved.scopeId()).as("institute scope ignores any id sent").isZero();
        assertThat(saved.enabled()).isTrue();
        assertThat(saved.faceVisibility()).isTrue();
        assertThat(saved.cameraRequired()).as("camera is optional unless asked").isFalse();
        assertThat(saved.warningAfterSeconds()).isEqualTo(10);
    }

    @Test
    void aBatchSettingNeedsToSayWhichBatch() {
        signInAs("ADMIN", null);

        assertThatThrownBy(() -> service.save(on(Scope.BATCH, null))).isInstanceOf(BusinessRuleException.class);
    }

    // ------------------------------------------------------------------ events

    private void joinedAsStudent() {
        signInAs("STUDENT", 5L);
        LiveParticipant p = new LiveParticipant();
        p.setDisplayName("Asha");
        lenient().when(participants.findByLiveSessionIdAndUserId(LIVE_SESSION, 7L)).thenReturn(Optional.of(p));
    }

    private void monitoringOn() {
        when(settings.findByScopeTypeAndScopeId(Scope.INSTITUTE, 0L))
                .thenReturn(Optional.of(setting(Scope.INSTITUTE, 0, true)));
    }

    @Test
    void aStudentsReportIsStoredWithSeverityAndPositionInTheClass() {
        joinedAsStudent();
        monitoringOn();

        service.record(CLASS_SESSION, new EventRequest(MonitoringEvent.Type.FACE_NOT_DETECTED, 75, "away"));

        var saved = org.mockito.ArgumentCaptor.forClass(MonitoringEvent.class);
        verify(eventRepository).save(saved.capture());
        assertThat(saved.getValue().getSeverity()).isEqualTo(MonitoringEvent.Severity.CRITICAL);
        assertThat(saved.getValue().getStudentId()).isEqualTo(5L);
        assertThat(saved.getValue().getOffsetSeconds()).isBetween(119, 125);
    }

    @Test
    void nothingIsStoredWhereMonitoringIsOff() {
        joinedAsStudent();

        service.record(CLASS_SESSION, new EventRequest(MonitoringEvent.Type.FACE_NOT_DETECTED, 20, null));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void faceEventsAreIgnoredWhenFaceVisibilityIsOffButCameraProblemsAreStillKept() {
        joinedAsStudent();
        var s = setting(Scope.INSTITUTE, 0, true);
        s.setFaceVisibility(false);
        when(settings.findByScopeTypeAndScopeId(Scope.INSTITUTE, 0L)).thenReturn(Optional.of(s));

        service.record(CLASS_SESSION, new EventRequest(MonitoringEvent.Type.FACE_NOT_DETECTED, 20, null));
        verify(eventRepository, never()).save(any());

        service.record(CLASS_SESSION, new EventRequest(MonitoringEvent.Type.CAMERA_DISABLED, null, null));
        verify(eventRepository).save(any(MonitoringEvent.class));
    }

    @Test
    void aStudentWhoNeverJoinedCannotWriteEvents() {
        signInAs("STUDENT", 5L);
        when(participants.findByLiveSessionIdAndUserId(LIVE_SESSION, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(CLASS_SESSION,
                new EventRequest(MonitoringEvent.Type.FACE_NOT_DETECTED, 20, null)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void nonStudentsCannotReportEvents() {
        signInAs("TRAINER", 3L);

        assertThatThrownBy(() -> service.record(CLASS_SESSION,
                new EventRequest(MonitoringEvent.Type.FACE_NOT_DETECTED, 20, null)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void reportsAfterTheClassEndedAreDropped() {
        joinedAsStudent();
        session.setStatus(LiveSessionStatus.ENDED);

        service.record(CLASS_SESSION, new EventRequest(MonitoringEvent.Type.CAMERA_DISABLED, null, null));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void oneStudentCannotFloodTheLog() {
        joinedAsStudent();
        monitoringOn();
        when(eventRepository.countByLiveSessionIdAndStudentId(eq(LIVE_SESSION), eq(5L)))
                .thenReturn((long) MonitoringService.MAX_EVENTS_PER_STUDENT);

        service.record(CLASS_SESSION, new EventRequest(MonitoringEvent.Type.MULTIPLE_FACES, null, null));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void readingTheLogIsAHostCheckInTheService() {
        signInAs("STUDENT", 5L);
        org.mockito.Mockito.doThrow(new ForbiddenOperationException("no")).when(hostAccess).requireHostOf(session);

        assertThatThrownBy(() -> service.eventsOf(CLASS_SESSION)).isInstanceOf(ForbiddenOperationException.class);
    }

    // ------------------------------------------------------------------ severity

    @Test
    void severityGrowsWithHowLongTheFaceWasGone() {
        assertThat(MonitoringRules.severity(MonitoringEvent.Type.FACE_NOT_DETECTED, 15))
                .isEqualTo(MonitoringEvent.Severity.WARNING);
        assertThat(MonitoringRules.severity(MonitoringEvent.Type.FACE_NOT_DETECTED, 60))
                .isEqualTo(MonitoringEvent.Severity.CRITICAL);
        assertThat(MonitoringRules.severity(MonitoringEvent.Type.FACE_RESTORED, 15))
                .isEqualTo(MonitoringEvent.Severity.INFO);
    }
}
