package com.itilms.batch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.batch.client.AdmissionClient;
import com.itilms.batch.client.CourseClient;
import com.itilms.batch.dto.request.CourseRequestDtos.ApproveRequest;
import com.itilms.batch.dto.request.CourseRequestDtos.CourseRequestResponse;
import com.itilms.batch.dto.request.CourseRequestDtos.CreateRequest;
import com.itilms.batch.dto.request.CourseRequestDtos.RejectRequest;
import com.itilms.batch.dto.request.EnrollStudentRequest;
import com.itilms.batch.dto.response.EnrollmentResultResponse;
import com.itilms.batch.entity.Batch;
import com.itilms.batch.entity.BatchStatus;
import com.itilms.batch.entity.CourseRequest;
import com.itilms.batch.entity.CourseRequestStatus;
import com.itilms.batch.entity.Enrollment;
import com.itilms.batch.entity.EnrollmentStatus;
import com.itilms.batch.repository.BatchRepository;
import com.itilms.batch.repository.CourseRequestRepository;
import com.itilms.batch.repository.EnrollmentRepository;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;

/**
 * A request decides whether someone gets into a class, so who may ask, who may decide, and
 * what an approval is allowed to do are pinned here. Student 10 asks for course 7; batch 5
 * belongs to course 7 and batch 6 to course 8.
 */
@ExtendWith(MockitoExtension.class)
class CourseRequestServiceTest {

    private static final long COURSE = 7L;

    private static final AppPrincipal ADMIN = new AppPrincipal(1L, "a@x", "Admin", "ADMIN", null);
    private static final AppPrincipal COORDINATOR = new AppPrincipal(2L, "c@x", "Coordinator", "COORDINATOR", null);
    private static final AppPrincipal TRAINER = new AppPrincipal(3L, "t@x", "Trainer", "TRAINER", 20L);
    private static final AppPrincipal STUDENT_10 = new AppPrincipal(4L, "s10@x", "Asha", "STUDENT", 10L);
    private static final AppPrincipal STUDENT_11 = new AppPrincipal(5L, "s11@x", "Ravi", "STUDENT", 11L);

    @Mock CourseRequestRepository requests;
    @Mock BatchRepository batches;
    @Mock EnrollmentRepository enrollments;
    @Mock BatchService batchService;
    @Mock CourseClient courseClient;
    @Mock AdmissionClient admissionClient;
    @Mock EventPublisher events;

    CourseRequestService service;

    @BeforeEach
    void setUp() {
        service = new CourseRequestService(requests, batches, enrollments, batchService, courseClient, admissionClient, events);

        lenient().when(courseClient.get(COURSE)).thenReturn(
                new CourseClient.CourseDetail(new CourseClient.CourseSummary(COURSE, "Java Full Stack", "JFS", "PUBLISHED")));
        lenient().when(admissionClient.lookupStudents(List.of(10L))).thenReturn(List.of(
                new AdmissionClient.StudentSummary(10L, 4L, "STU-10", "Asha Patil", "asha@x", "9000000010", "ACTIVE")));
        lenient().when(batches.findById(5L)).thenReturn(Optional.of(batch(5L, COURSE, BatchStatus.PLANNED)));
        lenient().when(batches.findById(6L)).thenReturn(Optional.of(batch(6L, 8L, BatchStatus.PLANNED)));
        lenient().when(enrollments.findActiveForStudent(10L)).thenReturn(List.of());
        lenient().when(requests.saveAndFlush(any(CourseRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        actAs(STUDENT_10);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // -----------------------------------------------------------------

    @Test
    @DisplayName("a student's request keeps the student and course as they were, and tells the staff")
    void createSnapshotsAndNotifiesStaff() {
        CourseRequestResponse response = service.create(new CreateRequest(COURSE, 5L, "  I want the morning batch  "));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.studentName()).isEqualTo("Asha Patil");
        assertThat(response.studentEmail()).isEqualTo("asha@x");
        assertThat(response.courseTitle()).isEqualTo("Java Full Stack");
        assertThat(response.preferredBatchId()).isEqualTo(5L);
        assertThat(response.message()).isEqualTo("I want the morning batch");
        verify(events).notifyRole(eq("ADMIN"), eq("COURSE_REQUEST"), any(), any(), any());
        verify(events).notifyRole(eq("COORDINATOR"), eq("COURSE_REQUEST"), any(), any(), any());
    }

    @Test
    @DisplayName("only a student can ask, and the request is always for the caller, never a body-supplied student")
    void nonStudentCannotAsk() {
        actAs(TRAINER);

        assertThatThrownBy(() -> service.create(new CreateRequest(COURSE, null, null)))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a draft or archived course cannot be requested")
    void unpublishedCourseRefused() {
        when(courseClient.get(COURSE)).thenReturn(
                new CourseClient.CourseDetail(new CourseClient.CourseSummary(COURSE, "Java", "JFS", "DRAFT")));

        assertThatThrownBy(() -> service.create(new CreateRequest(COURSE, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not open");
    }

    @Test
    @DisplayName("when course-service cannot answer, the request is refused rather than saved blind")
    void courseServiceDown() {
        when(courseClient.get(COURSE)).thenReturn(null);

        assertThatThrownBy(() -> service.create(new CreateRequest(COURSE, null, null)))
                .isInstanceOf(BusinessRuleException.class);
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a student already in the course, or with a request waiting, cannot ask again")
    void duplicatesRefused() {
        when(enrollments.findActiveForStudent(10L)).thenReturn(List.of(Enrollment.builder().courseId(COURSE).build()));
        assertThatThrownBy(() -> service.create(new CreateRequest(COURSE, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already enrolled");

        when(enrollments.findActiveForStudent(10L)).thenReturn(List.of());
        when(requests.existsByStudentIdAndCourseIdAndStatus(10L, COURSE, CourseRequestStatus.PENDING)).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CreateRequest(COURSE, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("waiting");
    }

    @Test
    @DisplayName("the batch a student asks for must belong to the course and still accept students")
    void preferredBatchMustFit() {
        assertThatThrownBy(() -> service.create(new CreateRequest(COURSE, 6L, null)))
                .isInstanceOf(BusinessRuleException.class);

        when(batches.findById(5L)).thenReturn(Optional.of(batch(5L, COURSE, BatchStatus.COMPLETED)));
        assertThatThrownBy(() -> service.create(new CreateRequest(COURSE, 5L, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // -----------------------------------------------------------------

    @Test
    @DisplayName("approving enrols the student through the normal enrolment path and records who decided")
    void approveEnrols() {
        CourseRequest pending = pending(100L, 10L);
        when(requests.findByIdForUpdate(100L)).thenReturn(Optional.of(pending));
        when(batchService.enroll(eq(5L), any(EnrollStudentRequest.class)))
                .thenReturn(new EnrollmentResultResponse(5L, 1, 0, 20, List.of(EnrollmentResultResponse.Outcome.ok(10L))));
        when(enrollments.findByStudentIdAndBatchIdAndStatus(10L, 5L, EnrollmentStatus.ACTIVE))
                .thenReturn(Optional.of(Enrollment.builder().id(900L).build()));
        actAs(COORDINATOR);

        CourseRequestResponse response = service.approve(100L, new ApproveRequest(5L, "Welcome"));

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.approvedBatchId()).isEqualTo(5L);
        assertThat(response.enrollmentId()).isEqualTo(900L);
        assertThat(response.decidedBy()).isEqualTo(2L);
        assertThat(response.decisionNote()).isEqualTo("Welcome");
        verify(batchService).enroll(eq(5L), eq(new EnrollStudentRequest(List.of(10L))));
    }

    @Test
    @DisplayName("with no batch chosen, approval uses the batch the student asked for")
    void approveUsesPreferredBatch() {
        CourseRequest pending = pending(100L, 10L);
        pending.setPreferredBatchId(5L);
        when(requests.findByIdForUpdate(100L)).thenReturn(Optional.of(pending));
        when(batchService.enroll(eq(5L), any(EnrollStudentRequest.class)))
                .thenReturn(new EnrollmentResultResponse(5L, 1, 0, 20, List.of(EnrollmentResultResponse.Outcome.ok(10L))));
        actAs(ADMIN);

        assertThat(service.approve(100L, new ApproveRequest(null, null)).approvedBatchId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("with no batch anywhere, approval asks for one instead of guessing")
    void approveNeedsABatch() {
        when(requests.findByIdForUpdate(100L)).thenReturn(Optional.of(pending(100L, 10L)));
        actAs(ADMIN);

        assertThatThrownBy(() -> service.approve(100L, new ApproveRequest(null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Choose the batch");
        verify(batchService, never()).enroll(any(), any());
    }

    @Test
    @DisplayName("a batch of a different course cannot be used")
    void approveWrongCourseBatch() {
        when(requests.findByIdForUpdate(100L)).thenReturn(Optional.of(pending(100L, 10L)));
        actAs(ADMIN);

        assertThatThrownBy(() -> service.approve(100L, new ApproveRequest(6L, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("different course");
        verify(batchService, never()).enroll(any(), any());
    }

    @Test
    @DisplayName("a full batch leaves the request waiting, with the reason")
    void approveFullBatchStaysPending() {
        CourseRequest pending = pending(100L, 10L);
        when(requests.findByIdForUpdate(100L)).thenReturn(Optional.of(pending));
        when(batchService.enroll(eq(5L), any(EnrollStudentRequest.class))).thenReturn(new EnrollmentResultResponse(5L, 0, 1, 0,
                List.of(EnrollmentResultResponse.Outcome.failed(10L, "The batch is full (capacity 30)"))));
        actAs(ADMIN);

        assertThatThrownBy(() -> service.approve(100L, new ApproveRequest(5L, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("full");
        assertThat(pending.getStatus()).isEqualTo(CourseRequestStatus.PENDING);
    }

    @Test
    @DisplayName("a decided request cannot be decided again")
    void cannotDecideTwice() {
        CourseRequest approved = pending(100L, 10L);
        approved.setStatus(CourseRequestStatus.APPROVED);
        when(requests.findByIdForUpdate(100L)).thenReturn(Optional.of(approved));
        actAs(ADMIN);

        assertThatThrownBy(() -> service.approve(100L, new ApproveRequest(5L, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already been approved");
        assertThatThrownBy(() -> service.reject(100L, new RejectRequest("no")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("students and trainers cannot approve or reject, whatever the controller says")
    void onlyStaffDecide() {
        for (AppPrincipal caller : List.of(STUDENT_10, TRAINER)) {
            actAs(caller);
            assertThatThrownBy(() -> service.approve(100L, new ApproveRequest(5L, null)))
                    .isInstanceOf(ForbiddenOperationException.class);
            assertThatThrownBy(() -> service.reject(100L, new RejectRequest("no")))
                    .isInstanceOf(ForbiddenOperationException.class);
            assertThatThrownBy(() -> service.list(null, org.springframework.data.domain.Pageable.unpaged()))
                    .isInstanceOf(ForbiddenOperationException.class);
        }
        verify(batchService, never()).enroll(any(), any());
    }

    @Test
    @DisplayName("rejecting records the reason and tells the student")
    void rejectNotifiesStudent() {
        CourseRequest pending = pending(100L, 10L);
        when(requests.findByIdForUpdate(100L)).thenReturn(Optional.of(pending));
        actAs(ADMIN);

        CourseRequestResponse response = service.reject(100L, new RejectRequest("  Batch is full this term  "));

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.decisionNote()).isEqualTo("Batch is full this term");
        verify(events).notifyUsers(eq(List.of(4L)), eq("COURSE_REQUEST"), any(), eq("Batch is full this term"), any());
        verify(batchService, never()).enroll(any(), any());
    }

    // -----------------------------------------------------------------

    @Test
    @DisplayName("a student can withdraw their own waiting request but not someone else's")
    void cancelOnlyOwn() {
        CourseRequest pending = pending(100L, 10L);
        when(requests.findByIdForUpdate(100L)).thenReturn(Optional.of(pending));

        assertThat(service.cancel(100L).status()).isEqualTo("CANCELLED");

        CourseRequest other = pending(101L, 10L);
        when(requests.findByIdForUpdate(101L)).thenReturn(Optional.of(other));
        actAs(STUDENT_11);
        assertThatThrownBy(() -> service.cancel(101L)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(other.getStatus()).isEqualTo(CourseRequestStatus.PENDING);
    }

    @Test
    @DisplayName("a student sees their own request, not another student's; staff see any")
    void getIsScoped() {
        when(requests.findById(100L)).thenReturn(Optional.of(pending(100L, 10L)));

        assertThat(service.get(100L).id()).isEqualTo(100L);

        actAs(STUDENT_11);
        assertThatThrownBy(() -> service.get(100L)).isInstanceOf(ResourceNotFoundException.class);

        actAs(COORDINATOR);
        assertThat(service.get(100L).studentName()).isEqualTo("Asha Patil");
    }

    @Test
    @DisplayName("my requests are looked up by the caller's own student id")
    void mineUsesCallersId() {
        when(requests.findByStudentIdOrderByIdDesc(10L)).thenReturn(List.of(pending(100L, 10L)));

        assertThat(service.mine()).hasSize(1);

        actAs(ADMIN);
        assertThat(service.mine()).isEmpty();
    }

    // -----------------------------------------------------------------

    private static Batch batch(Long id, Long courseId, BatchStatus status) {
        return Batch.builder().id(id).courseId(courseId).status(status).build();
    }

    private static CourseRequest pending(Long id, Long studentId) {
        return CourseRequest.builder().id(id).studentId(studentId).userId(4L).studentName("Asha Patil")
                .courseId(COURSE).courseTitle("Java Full Stack").build();
    }

    private static void actAs(AppPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }
}
