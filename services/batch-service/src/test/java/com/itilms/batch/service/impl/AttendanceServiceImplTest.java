package com.itilms.batch.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.itilms.batch.dto.request.MarkAttendanceRequest;
import com.itilms.batch.dto.request.MarkAttendanceRequest.Entry;
import com.itilms.batch.dto.response.AttendanceResponse;
import com.itilms.batch.dto.response.AttendanceSummaryResponse;
import com.itilms.batch.entity.Attendance;
import com.itilms.batch.entity.AttendanceSource;
import com.itilms.batch.entity.AttendanceStatus;
import com.itilms.batch.entity.ClassSession;
import com.itilms.batch.entity.Enrollment;
import com.itilms.batch.entity.SessionStatus;
import com.itilms.batch.repository.AttendanceRepository;
import com.itilms.batch.repository.ClassSessionRepository;
import com.itilms.batch.repository.EnrollmentRepository;
import com.itilms.batch.service.BatchService;
import com.itilms.common.event.AttendanceMarkedEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.LiveAttendanceComputedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;

/**
 * Attendance is what certificates, placements and fee reminders are judged on, so the
 * arithmetic and the "who may change it, and with what reason" rules are pinned here.
 *
 * <p>Batch 5 has students 10, 11 and 12 on its active register; session 100 belongs to it.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {

    private static final long BATCH = 5L;
    private static final long SESSION = 100L;

    private static final AppPrincipal ADMIN = new AppPrincipal(1L, "a@x", "Admin", "ADMIN", null);
    private static final AppPrincipal TRAINER = new AppPrincipal(3L, "t@x", "Trainer", "TRAINER", 20L);
    private static final AppPrincipal STUDENT_10 = new AppPrincipal(4L, "s10@x", "Asha", "STUDENT", 10L);

    @Mock AttendanceRepository attendanceRepository;
    @Mock ClassSessionRepository sessionRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock BatchService batchService;
    @Mock EventPublisher events;

    AttendanceServiceImpl service;
    ClassSession session;

    @BeforeEach
    void setUp() {
        service = new AttendanceServiceImpl(attendanceRepository, sessionRepository, enrollmentRepository, batchService, events);
        ReflectionTestUtils.setField(service, "alertThreshold", 75.0);

        session = ClassSession.builder().id(SESSION).batchId(BATCH).sessionDate(LocalDate.of(2026, 9, 1)).topic("Streams").build();
        lenient().when(sessionRepository.findById(SESSION)).thenReturn(Optional.of(session));
        lenient().when(enrollmentRepository.findActiveStudentIds(BATCH)).thenReturn(List.of(10L, 11L, 12L));
        lenient().when(enrollmentRepository.findActiveRoster(BATCH)).thenReturn(List.of(enrolled(10, "Asha"), enrolled(11, "Ravi"), enrolled(12, "Meera")));
        lenient().when(batchService.batchIdsForTrainer(20L)).thenReturn(List.of(BATCH));
        lenient().when(batchService.activeBatchIdsForStudent(10L)).thenReturn(List.of(BATCH));
        lenient().when(attendanceRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        actAs(ADMIN);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void actAs(AppPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static Enrollment enrolled(long studentId, String name) {
        return Enrollment.builder().studentId(studentId).studentName(name).batchId(BATCH).build();
    }

    private static MarkAttendanceRequest marks(String reason, Entry... entries) {
        return new MarkAttendanceRequest(List.of(entries), reason);
    }

    private static Entry entry(long studentId, String status) {
        return new Entry(studentId, status, null);
    }

    private static Attendance row(long studentId, AttendanceStatus status, AttendanceSource source) {
        return Attendance.builder().sessionId(SESSION).studentId(studentId).status(status).source(source).build();
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Marking the register")
    class Marking {

        @Test
        void firstMarkingRecordsEveryoneAndFlagsTheSession() {
            when(attendanceRepository.findBySessionId(SESSION)).thenReturn(List.of());

            List<AttendanceResponse> result = service.mark(SESSION, marks(null,
                    entry(10, "PRESENT"), entry(11, "absent"), entry(12, " Late ")));

            assertThat(result).hasSize(3);
            ArgumentCaptor<List<Attendance>> saved = ArgumentCaptor.forClass(List.class);
            verify(attendanceRepository).saveAll(saved.capture());
            assertThat(saved.getValue()).extracting(Attendance::getStatus)
                    .containsExactly(AttendanceStatus.PRESENT, AttendanceStatus.ABSENT, AttendanceStatus.LATE);
            assertThat(saved.getValue()).allSatisfy(a -> {
                assertThat(a.getSource()).isEqualTo(AttendanceSource.MANUAL);
                assertThat(a.getMarkedBy()).isEqualTo(1L);
            });
            assertThat(session.isAttendanceMarked()).isTrue();
            assertThat(session.isAttendanceAuto()).isFalse();

            ArgumentCaptor<AttendanceMarkedEvent> event = ArgumentCaptor.forClass(AttendanceMarkedEvent.class);
            verify(events).publishAfterCommit(eq(KafkaTopics.ATTENDANCE_MARKED), event.capture());
            assertThat(event.getValue().correction()).isFalse();
            verify(events).audit(eq("batch-service"), eq("ATTENDANCE_MARKED"), eq("ClassSession"), eq(SESSION), any(), any());
        }

        @Test
        void changingASavedRegisterWithoutAReasonIsRefused() {
            session.setAttendanceMarked(true);

            assertThatThrownBy(() -> service.mark(SESSION, marks(null, entry(10, "ABSENT"))))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("correctionReason");

            verify(attendanceRepository, never()).saveAll(any());
            verify(events, never()).audit(anyString(), anyString(), anyString(), any(), any(), any());
        }

        @Test
        void aBlankReasonCountsAsNoReason() {
            session.setAttendanceMarked(true);

            assertThatThrownBy(() -> service.mark(SESSION, marks("   ", entry(10, "ABSENT"))))
                    .isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> service.mark(SESSION, marks("", entry(10, "ABSENT"))))
                    .isInstanceOf(BusinessRuleException.class);
            verify(attendanceRepository, never()).saveAll(any());
        }

        @Test
        @SuppressWarnings("unchecked")
        void aCorrectionWithAReasonChangesTheRowAndIsAuditedWithBeforeAndAfter() {
            session.setAttendanceMarked(true);
            Attendance existing = row(10, AttendanceStatus.ABSENT, AttendanceSource.MANUAL);
            when(attendanceRepository.findBySessionId(SESSION)).thenReturn(List.of(existing));

            service.mark(SESSION, marks("Marked the wrong row", entry(10, "PRESENT")));

            assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(existing.wasCorrected()).isTrue();
            assertThat(existing.getCorrectedBy()).isEqualTo(1L);

            ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
            ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
            verify(events).audit(eq("batch-service"), eq("ATTENDANCE_CORRECTED"), eq("ClassSession"), eq(SESSION),
                    before.capture(), after.capture());
            assertThat((Map<String, Object>) before.getValue()).containsEntry("10", "ABSENT");
            assertThat((Map<String, Object>) after.getValue()).containsEntry("10", "PRESENT").containsEntry("reason", "Marked the wrong row");

            ArgumentCaptor<AttendanceMarkedEvent> event = ArgumentCaptor.forClass(AttendanceMarkedEvent.class);
            verify(events).publishAfterCommit(eq(KafkaTopics.ATTENDANCE_MARKED), event.capture());
            assertThat(event.getValue().correction()).isTrue();
        }

        @Test
        void aStudentWhoIsNotOnTheActiveRegisterCannotBeMarked() {
            assertThatThrownBy(() -> service.mark(SESSION, marks(null, entry(10, "PRESENT"), entry(99, "PRESENT"))))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("99");
            verify(attendanceRepository, never()).saveAll(any());
        }

        @Test
        void anUnknownStatusIsRefused() {
            when(attendanceRepository.findBySessionId(SESSION)).thenReturn(List.of());

            assertThatThrownBy(() -> service.mark(SESSION, marks(null, entry(10, "MAYBE"))))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("PRESENT, ABSENT, LATE or EXCUSED");
        }

        @Test
        void aCancelledSessionTakesNoAttendance() {
            session.setStatus(SessionStatus.CANCELLED);

            assertThatThrownBy(() -> service.mark(SESSION, marks(null, entry(10, "PRESENT"))))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cancelled");
        }

        @Test
        void anUnknownSessionIsNotFound() {
            when(sessionRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.mark(404L, marks(null, entry(10, "PRESENT"))))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void studentsCannotMark() {
            actAs(STUDENT_10);

            assertThatThrownBy(() -> service.mark(SESSION, marks(null, entry(10, "PRESENT"))))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        @Test
        void aTrainerMayMarkOnlyTheirOwnBatches() {
            when(attendanceRepository.findBySessionId(SESSION)).thenReturn(List.of());
            actAs(TRAINER);
            service.mark(SESSION, marks(null, entry(10, "PRESENT")));
            verify(attendanceRepository).saveAll(any());

            when(batchService.batchIdsForTrainer(20L)).thenReturn(List.of(99L));
            assertThatThrownBy(() -> service.mark(SESSION, marks(null, entry(11, "PRESENT"))))
                    .isInstanceOf(ForbiddenOperationException.class).hasMessageContaining("assigned to you");
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Attendance derived from a live class")
    class LiveClass {

        private LiveAttendanceComputedEvent live(LiveAttendanceComputedEvent.Entry... entries) {
            return new LiveAttendanceComputedEvent("ev-1", Instant.now(), 7L, SESSION, BATCH, 3600, List.of(entries));
        }

        private LiveAttendanceComputedEvent.Entry joined(long studentId, int seconds, int percent, String status) {
            return new LiveAttendanceComputedEvent.Entry(studentId, studentId + 1000, seconds, percent, status);
        }

        @Test
        void writesThoseWhoJoinedAndMarksEveryoneElseAbsent() {
            when(attendanceRepository.findBySessionId(SESSION)).thenReturn(List.of());

            service.applyLiveAttendance(live(joined(10, 2520, 72, "PRESENT")));

            ArgumentCaptor<List<Attendance>> saved = ArgumentCaptor.forClass(List.class);
            verify(attendanceRepository).saveAll(saved.capture());
            List<Attendance> rows = new ArrayList<>(saved.getValue());
            assertThat(rows).hasSize(3);
            Attendance asha = rows.stream().filter(a -> a.getStudentId() == 10).findFirst().orElseThrow();
            assertThat(asha.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(asha.getAttendedMinutes()).isEqualTo(42);
            assertThat(asha.getSource()).isEqualTo(AttendanceSource.LIVE_CLASS);
            assertThat(rows).filteredOn(a -> a.getStudentId() != 10)
                    .allSatisfy(a -> assertThat(a.getStatus()).isEqualTo(AttendanceStatus.ABSENT));
            assertThat(session.isAttendanceMarked()).isTrue();
            assertThat(session.isAttendanceAuto()).isTrue();
        }

        @Test
        void aTrainersHandMarkIsNeverOverwrittenByTheMachine() {
            Attendance byHand = row(10, AttendanceStatus.PRESENT, AttendanceSource.MANUAL);
            when(attendanceRepository.findBySessionId(SESSION)).thenReturn(List.of(byHand));

            service.applyLiveAttendance(live(joined(10, 60, 2, "ABSENT")));

            assertThat(byHand.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(byHand.getSource()).isEqualTo(AttendanceSource.MANUAL);
        }

        @Test
        void aCorrectedRowIsAlsoLeftAlone() {
            Attendance corrected = row(10, AttendanceStatus.LATE, AttendanceSource.LIVE_CLASS);
            corrected.correct(AttendanceStatus.PRESENT, "Joined by phone", 3L);
            when(attendanceRepository.findBySessionId(SESSION)).thenReturn(List.of(corrected));

            service.applyLiveAttendance(live(joined(10, 60, 2, "ABSENT")));

            assertThat(corrected.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        }

        @Test
        void anEarlierAutomaticRowIsRefreshed() {
            Attendance auto = row(10, AttendanceStatus.ABSENT, AttendanceSource.LIVE_CLASS);
            when(attendanceRepository.findBySessionId(SESSION)).thenReturn(List.of(auto));

            service.applyLiveAttendance(live(joined(10, 3000, 83, "PRESENT")));

            assertThat(auto.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(auto.getAttendedMinutes()).isEqualTo(50);
        }

        @Test
        void peopleNotOnTheRegisterAreIgnored() {
            when(attendanceRepository.findBySessionId(SESSION)).thenReturn(List.of());

            service.applyLiveAttendance(live(joined(10, 3000, 83, "PRESENT"), joined(77, 3000, 83, "PRESENT")));

            ArgumentCaptor<List<Attendance>> saved = ArgumentCaptor.forClass(List.class);
            verify(attendanceRepository).saveAll(saved.capture());
            assertThat(saved.getValue()).extracting(Attendance::getStudentId).doesNotContain(77L);
        }

        @Test
        void anUnknownSessionIsIgnoredWithoutError() {
            when(sessionRepository.findById(404L)).thenReturn(Optional.empty());

            service.applyLiveAttendance(new LiveAttendanceComputedEvent("ev", Instant.now(), 7L, 404L, BATCH, 3600, List.of()));

            verify(attendanceRepository, never()).saveAll(any());
        }

        @Test
        void aRedeliveredEventThatChangesNothingWritesNothing() {
            when(attendanceRepository.findBySessionId(SESSION)).thenReturn(List.of(
                    row(10, AttendanceStatus.PRESENT, AttendanceSource.MANUAL),
                    row(11, AttendanceStatus.ABSENT, AttendanceSource.MANUAL),
                    row(12, AttendanceStatus.ABSENT, AttendanceSource.MANUAL)));

            service.applyLiveAttendance(live(joined(10, 3000, 83, "PRESENT")));

            verify(attendanceRepository, never()).saveAll(any());
            verify(events, never()).publishAfterCommit(anyString(), any());
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Attendance percentage")
    class Summary {

        @Test
        void threeOfFourIsSeventyFivePercentAndNotBelowTheThreshold() {
            when(attendanceRepository.attendanceTotalsForBatch(10L, BATCH)).thenReturn(rows(new Object[]{3L, 4L}));
            when(enrollmentRepository.findByStudentIdAndBatchIdAndStatus(eq(10L), eq(BATCH), any()))
                    .thenReturn(Optional.of(enrolled(10, "Asha")));
            actAs(STUDENT_10);

            AttendanceSummaryResponse summary = service.studentSummary(10L, BATCH);

            assertThat(summary.attendedSessions()).isEqualTo(3);
            assertThat(summary.totalSessions()).isEqualTo(4);
            assertThat(summary.attendancePercent()).isEqualByComparingTo("75.00");
            assertThat(summary.belowThreshold()).as("75 is not below 75").isFalse();
            assertThat(summary.studentName()).isEqualTo("Asha");
        }

        @Test
        void twoOfThreeRoundsToTwoDecimalsAndIsBelowTheThreshold() {
            when(attendanceRepository.attendanceTotalsForBatch(10L, BATCH)).thenReturn(rows(new Object[]{2L, 3L}));
            actAs(STUDENT_10);

            AttendanceSummaryResponse summary = service.studentSummary(10L, BATCH);

            assertThat(summary.attendancePercent()).isEqualByComparingTo(new BigDecimal("66.67"));
            assertThat(summary.belowThreshold()).isTrue();
        }

        /** Regression: a single-row, two-column query once came back nested and threw ClassCastException. */
        @Test
        void aStudentWithNoAttendanceRowsIsZeroOfZeroNotAnError() {
            when(attendanceRepository.attendanceTotalsForBatch(10L, BATCH)).thenReturn(List.of());
            actAs(STUDENT_10);

            AttendanceSummaryResponse summary = service.studentSummary(10L, BATCH);

            assertThat(summary.totalSessions()).isZero();
            assertThat(summary.attendancePercent()).isEqualByComparingTo("0");
            assertThat(summary.belowThreshold()).as("nothing held yet, nothing to judge").isFalse();
        }

        @Test
        void aSumOverNoRowsComesBackNullAndCountsAsZero() {
            when(attendanceRepository.attendanceTotalsForBatch(10L, BATCH)).thenReturn(rows(new Object[]{null, null}));
            actAs(STUDENT_10);

            AttendanceSummaryResponse summary = service.studentSummary(10L, BATCH);

            assertThat(summary.attendedSessions()).isZero();
            assertThat(summary.totalSessions()).isZero();
        }

        @Test
        void aStudentCannotReadAnotherStudentsPercentage() {
            actAs(STUDENT_10);

            assertThatThrownBy(() -> service.studentSummary(11L, BATCH)).isInstanceOf(ForbiddenOperationException.class);
            verify(attendanceRepository, never()).attendanceTotalsForBatch(anyLong(), anyLong());
        }

        @Test
        void staffCanReadAnyStudentsPercentage() {
            when(attendanceRepository.attendanceTotalsForBatch(11L, BATCH)).thenReturn(rows(new Object[]{1L, 2L}));

            assertThat(service.studentSummary(11L, BATCH).attendancePercent()).isEqualByComparingTo("50");
        }

        @Test
        void theBatchListStartsFromTheRosterSoAnUnmarkedStudentShowsAsZeroOfZero() {
            List<Object[]> totals = new ArrayList<>();
            totals.add(new Object[]{10L, 4L, 4L});
            totals.add(new Object[]{11L, 1L, 4L});
            when(attendanceRepository.attendanceTotalsForAllInBatch(BATCH)).thenReturn(totals);

            List<AttendanceSummaryResponse> list = service.batchSummary(BATCH);

            assertThat(list).extracting(AttendanceSummaryResponse::studentId).containsExactlyInAnyOrder(10L, 11L, 12L);
            assertThat(list).as("lowest attendance first").extracting(AttendanceSummaryResponse::studentId).startsWith(12L);
            assertThat(list.stream().filter(s -> s.studentId() == 12L).findFirst().orElseThrow().totalSessions()).isZero();
        }

        @Test
        void alertsListOnlyThoseBelowTheThreshold() {
            List<Object[]> totals = new ArrayList<>();
            totals.add(new Object[]{10L, 4L, 4L});
            totals.add(new Object[]{11L, 1L, 4L});
            when(attendanceRepository.attendanceTotalsForAllInBatch(BATCH)).thenReturn(totals);

            assertThat(service.attendanceAlerts(BATCH)).extracting(AttendanceSummaryResponse::studentId).containsExactly(11L);
        }

        @Test
        void aStudentOutsideTheBatchCannotSeeItsSummaryAndATrainerOfAnotherBatchNeither() {
            actAs(STUDENT_10);
            when(batchService.activeBatchIdsForStudent(10L)).thenReturn(List.of(99L));
            assertThatThrownBy(() -> service.batchSummary(BATCH)).isInstanceOf(ForbiddenOperationException.class);

            actAs(TRAINER);
            when(batchService.batchIdsForTrainer(20L)).thenReturn(List.of(99L));
            assertThatThrownBy(() -> service.batchSummary(BATCH)).isInstanceOf(ForbiddenOperationException.class);
        }

        private List<Object[]> rows(Object[] row) {
            List<Object[]> list = new ArrayList<>();
            list.add(row);
            return list;
        }
    }
}
