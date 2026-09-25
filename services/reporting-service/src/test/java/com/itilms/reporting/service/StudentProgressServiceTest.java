package com.itilms.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.reporting.client.AdmissionClient;
import com.itilms.reporting.client.AssessmentClient;
import com.itilms.reporting.client.BatchClient;
import com.itilms.reporting.client.CourseClient;
import com.itilms.reporting.client.LiveClient;
import com.itilms.reporting.config.ReportingProperties;
import com.itilms.reporting.repository.StudentSessionAttendanceRepository;

/** Who may see whose report, what a student's own copy holds back, and that a missing service does not sink the rest. */
class StudentProgressServiceTest {

    private static final AppPrincipal ADMIN = new AppPrincipal(1L, "a@x", "Admin", "ADMIN", null);
    private static final AppPrincipal COORDINATOR = new AppPrincipal(2L, "c@x", "Coordinator", "COORDINATOR", null);
    private static final AppPrincipal TRAINER = new AppPrincipal(3L, "t@x", "Trainer", "TRAINER", 20L);
    private static final AppPrincipal STUDENT_10 = new AppPrincipal(4L, "s10@x", "Asha", "STUDENT", 10L);
    private static final AppPrincipal STUDENT_11 = new AppPrincipal(5L, "s11@x", "Ravi", "STUDENT", 11L);
    private static final AppPrincipal FINANCE = new AppPrincipal(6L, "f@x", "Finance", "FINANCE", null);

    private BatchClient batches;
    private CourseClient courses;
    private LiveClient live;
    private AssessmentClient assessments;
    private AdmissionClient admission;
    private StudentSessionAttendanceRepository attendance;
    private StudentProgressService service;

    @BeforeEach
    void setUp() {
        batches = mock(BatchClient.class);
        courses = mock(CourseClient.class);
        live = mock(LiveClient.class);
        assessments = mock(AssessmentClient.class);
        admission = mock(AdmissionClient.class);
        attendance = mock(StudentSessionAttendanceRepository.class);
        service = new StudentProgressService(batches, courses, live, assessments, admission, attendance, new ReportingProperties());

        // Student 10 is in batch 5 (course 3, active) and batch 6 (course 4, completed).
        when(batches.enrollmentsOf(10L)).thenReturn(List.of(
                new BatchClient.Enrollment(1L, "ACTIVE", 5L, "JFS-01", "Java", "ONGOING", 3L, "Java", "Rohan", "OFFLINE", null, null),
                new BatchClient.Enrollment(2L, "COMPLETED", 6L, "WEB-01", "Web", "COMPLETED", 4L, "Web", "Asha", "ONLINE", null, null)));
        when(courses.progressOf(10L)).thenReturn(List.of());
        when(live.participation(eq(10L), any())).thenReturn(new LiveClient.Participation(0, 0, 0, null, null));
        when(assessments.performance(eq(10L), any(), any(), anyBoolean())).thenReturn(new AssessmentClient.Performance(
                new AssessmentClient.Tests(0, 0, null, null, 0, 0, List.of(), List.of()),
                new AssessmentClient.Coding(0, 0, 0, null, List.of()),
                new AssessmentClient.Assignments(0, 0, 0, null, 0, 0, List.of())));
        when(admission.lookup(List.of(10L))).thenReturn(List.of(
                new AdmissionClient.StudentSummary(10L, 4L, "STU-10", "Asha Patil", "asha@x", "9000000010", "ACTIVE")));
        when(attendance.countsByBatchAndStatus(10L)).thenReturn(List.<Object[]>of());
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

    // ------------------------------------------------------------------ who may see what

    @Test
    @DisplayName("Administrators and coordinators may see any student's report")
    void staffSeeAnyone() {
        assertThat(service.of(10L).profile().fullName()).isEqualTo("Asha Patil");

        actAs(COORDINATOR);
        assertThat(service.of(10L).studentId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("A student sees only their own report, and cannot open a classmate's by changing the id")
    void studentOnlyOwn() {
        actAs(STUDENT_10);
        assertThat(service.mine().studentId()).isEqualTo(10L);
        assertThat(service.of(10L).studentId()).isEqualTo(10L);

        clearInvocations(assessments);
        actAs(STUDENT_11);
        assertThatThrownBy(() -> service.of(10L)).isInstanceOf(ForbiddenOperationException.class);
        verify(assessments, never()).performance(eq(10L), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Only a student has a report of their own")
    void mineIsForStudents() {
        actAs(ADMIN);
        assertThatThrownBy(() -> service.mine()).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("Other roles (finance, placement) may not see academic progress")
    void otherRolesRefused() {
        actAs(FINANCE);

        assertThatThrownBy(() -> service.of(10L)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("A trainer sees a student only through a batch they teach")
    void trainerOnlyTheirOwnStudents() {
        actAs(TRAINER);
        when(batches.myBatches()).thenReturn(List.of(new BatchClient.MyBatch(5L)));
        assertThat(service.of(10L).studentId()).isEqualTo(10L);

        when(batches.myBatches()).thenReturn(List.of(new BatchClient.MyBatch(99L)));
        assertThatThrownBy(() -> service.of(10L)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("A batch the student has since completed still lets its trainer see them")
    void trainerOfACompletedBatch() {
        actAs(TRAINER);
        when(batches.myBatches()).thenReturn(List.of(new BatchClient.MyBatch(6L)));

        assertThat(service.of(10L).studentId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("When a trainer's batches cannot be checked, the answer is no, not a guess")
    void trainerCheckFailsClosed() {
        actAs(TRAINER);
        when(batches.myBatches()).thenReturn(null);

        assertThatThrownBy(() -> service.of(10L)).isInstanceOf(BusinessRuleException.class);
        verify(assessments, never()).performance(any(), any(), any(), anyBoolean());
    }

    // ------------------------------------------------------------------ what is asked of the other services

    @Test
    @DisplayName("Only active enrolments scope the live and assessment questions")
    void onlyActiveBatchesAreAsked() {
        service.of(10L);

        verify(live).participation(10L, List.of(5L));
        verify(assessments).performance(10L, List.of(5L), List.of(3L), false);
    }

    @Test
    @DisplayName("A student's own copy asks for held-back results to stay held back; staff's does not")
    void studentCopyHoldsBackResults() {
        actAs(STUDENT_10);
        service.mine();
        verify(assessments).performance(10L, List.of(5L), List.of(3L), true);

        actAs(ADMIN);
        service.of(10L);
        verify(assessments).performance(10L, List.of(5L), List.of(3L), false);
    }

    @Test
    @DisplayName("A student in no batch has no live classes asked about")
    void noBatchNoLiveCall() {
        when(batches.enrollmentsOf(10L)).thenReturn(List.of());

        var report = service.of(10L);

        assertThat(report.liveClasses().sessionsHeld()).isZero();
        verify(live, never()).participation(any(), any());
    }

    // ------------------------------------------------------------------ assembly

    @Test
    @DisplayName("A service that is down leaves its section empty and named, and the rest of the report stands")
    void unavailableSectionsAreNamed() {
        when(courses.progressOf(10L)).thenReturn(null);
        when(assessments.performance(eq(10L), any(), any(), anyBoolean())).thenReturn(null);
        when(admission.lookup(any())).thenReturn(null);

        var report = service.of(10L);

        assertThat(report.unavailable()).containsExactlyInAnyOrder("courses", "assessments", "profile");
        assertThat(report.courses()).isNull();
        assertThat(report.tests()).isNull();
        assertThat(report.enrollments()).hasSize(2);
        assertThat(report.indicators()).hasSize(7);
        assertThat(report.suggestions()).isNotEmpty();
    }

    @Test
    @DisplayName("Attendance is worked out from the register: late counts as attended, excused is left out, per batch and overall")
    void attendanceFromTheRegister() {
        when(attendance.countsByBatchAndStatus(10L)).thenReturn(List.<Object[]>of(
                new Object[] {5L, "PRESENT", 6L}, new Object[] {5L, "LATE", 2L}, new Object[] {5L, "ABSENT", 2L},
                new Object[] {5L, "EXCUSED", 3L}, new Object[] {6L, "PRESENT", 5L}, new Object[] {6L, "ABSENT", 5L}));

        var attendance = service.of(10L).attendance();

        // Batch 5: 8 of 10 counted (the 3 excused are not in it) = 80%. Batch 6: 5 of 10 = 50%. Overall 13 of 20 = 65%.
        assertThat(attendance.percent()).isEqualTo(65);
        assertThat(attendance.attended()).isEqualTo(13);
        assertThat(attendance.counted()).isEqualTo(20);
        assertThat(attendance.excused()).isEqualTo(3);
        assertThat(attendance.byBatch()).extracting(b -> b.batchCode() + ":" + b.percent())
                .containsExactly("JFS-01:80", "WEB-01:50");
        // 65% is below the institute's 75%, so the report says so.
        assertThat(service.of(10L).suggestions()).extracting(s -> s.type()).contains("IMPROVE_ATTENDANCE");
    }

    @Test
    @DisplayName("No register entries means no attendance figure, not 0%")
    void noRegister() {
        var attendance = service.of(10L).attendance();

        assertThat(attendance.percent()).isNull();
        assertThat(attendance.counted()).isZero();
    }
}
