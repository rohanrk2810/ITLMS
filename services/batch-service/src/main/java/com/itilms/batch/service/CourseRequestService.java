package com.itilms.batch.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.batch.client.AdmissionClient;
import com.itilms.batch.client.CourseClient;
import com.itilms.batch.client.FinanceClient;
import com.itilms.batch.dto.request.CourseRequestDtos.ApproveRequest;
import com.itilms.batch.dto.request.CourseRequestDtos.CourseRequestResponse;
import com.itilms.batch.dto.request.CourseRequestDtos.CreateRequest;
import com.itilms.batch.dto.request.CourseRequestDtos.OpenBatchResponse;
import com.itilms.batch.dto.request.CourseRequestDtos.PaidJoinRequest;
import com.itilms.batch.dto.request.CourseRequestDtos.PaidJoinResponse;
import com.itilms.batch.dto.request.CourseRequestDtos.RejectRequest;
import com.itilms.batch.dto.request.EnrollStudentRequest;
import com.itilms.batch.dto.response.EnrollmentResultResponse;
import com.itilms.batch.entity.Batch;
import com.itilms.batch.entity.CourseRequest;
import com.itilms.batch.entity.CourseRequestStatus;
import com.itilms.batch.entity.EnrollmentStatus;
import com.itilms.batch.repository.BatchRepository;
import com.itilms.batch.repository.CourseRequestRepository;
import com.itilms.batch.repository.EnrollmentRepository;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.Roles;
import com.itilms.common.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * A student asks to join a course; an administrator or coordinator approves or rejects.
 *
 * <p>Approving goes through {@link BatchService#enroll}, so a request gets exactly the checks a
 * coordinator's own enrolment would: the batch must still accept students, have a free seat, and
 * the student must not already be in it. The controller limits who may decide; this class checks
 * again who is asking, because a role annotation on a controller is not the only door in.
 */
@Service
@RequiredArgsConstructor
public class CourseRequestService {

    private static final String SERVICE_NAME = "batch-service";
    private static final String PUBLISHED = "PUBLISHED";

    private final CourseRequestRepository requests;
    private final BatchRepository batches;
    private final EnrollmentRepository enrollments;
    private final BatchService batchService;
    private final CourseClient courseClient;
    private final AdmissionClient admissionClient;
    private final FinanceClient financeClient;
    private final EventPublisher events;

    // -----------------------------------------------------------------
    // Student
    // -----------------------------------------------------------------

    @Transactional
    public CourseRequestResponse create(CreateRequest request) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        if (!me.isStudent() || me.profileId() == null) {
            throw new ForbiddenOperationException("Only a student can ask to join a course.");
        }
        Long studentId = me.profileId();

        CourseClient.CourseDetail detail = courseClient.get(request.courseId());
        if (detail == null || detail.course() == null) {
            throw new BusinessRuleException("That course could not be found. Please try again shortly.");
        }
        CourseClient.CourseSummary course = detail.course();
        if (!PUBLISHED.equals(course.status())) {
            throw new BusinessRuleException("This course is not open for requests.");
        }

        if (request.batchId() != null) {
            Batch preferred = batches.findById(request.batchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Batch", request.batchId()));
            if (!preferred.getCourseId().equals(course.id()) || !preferred.getStatus().acceptsEnrollment()) {
                throw new BusinessRuleException("That batch is not open for this course.");
            }
        }

        boolean alreadyIn = enrollments.findActiveForStudent(studentId).stream()
                .anyMatch(e -> course.id().equals(e.getCourseId()));
        if (alreadyIn) {
            throw new BusinessRuleException("You are already enrolled in this course.");
        }
        if (requests.existsByStudentIdAndCourseIdAndStatus(studentId, course.id(), CourseRequestStatus.PENDING)) {
            throw new BusinessRuleException("You already have a request waiting for this course.");
        }

        var student = admissionClient.lookupStudents(List.of(studentId)).stream()
                .filter(s -> studentId.equals(s.id())).findFirst()
                .orElseThrow(() -> new BusinessRuleException("Your student record could not be found."));

        CourseRequest saved;
        try {
            saved = requests.saveAndFlush(CourseRequest.builder()
                    .studentId(studentId)
                    .userId(me.userId())
                    .studentCode(student.studentCode())
                    .studentName(student.fullName())
                    .studentEmail(student.email())
                    .studentPhone(student.phone())
                    .courseId(course.id())
                    .courseCode(course.code())
                    .courseTitle(course.title())
                    .preferredBatchId(request.batchId())
                    .message(blankToNull(request.message()))
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Two clicks at once: the unique index on open requests caught the second.
            throw new BusinessRuleException("You already have a request waiting for this course.");
        }

        String who = student.fullName() + " asked to join " + course.title();
        events.notifyRole(Roles.ADMIN, "COURSE_REQUEST", "New course request", who, "/course-requests");
        events.notifyRole(Roles.COORDINATOR, "COURSE_REQUEST", "New course request", who, "/course-requests");
        return CourseRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CourseRequestResponse> mine() {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        if (!me.isStudent() || me.profileId() == null) {
            return List.of();
        }
        return requests.findByStudentIdOrderByIdDesc(me.profileId()).stream().map(CourseRequestResponse::from).toList();
    }

    @Transactional
    public CourseRequestResponse cancel(Long id) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        CourseRequest request = requests.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course request", id));
        if (!me.isStudent() || !request.getStudentId().equals(me.profileId())) {
            // Someone else's request is reported as missing, not as forbidden: it should not be confirmed to exist.
            throw new ResourceNotFoundException("Course request", id);
        }
        requireOpen(request);
        request.setStatus(CourseRequestStatus.CANCELLED);
        request.setDecidedAt(Instant.now());
        return CourseRequestResponse.from(request);
    }

    // -----------------------------------------------------------------
    // Administrator / coordinator
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<CourseRequestResponse> list(CourseRequestStatus status, Pageable pageable) {
        requireStaff(SecurityUtils.requirePrincipal());
        var page = status == null
                ? requests.findAllByOrderByIdDesc(pageable)
                : requests.findByStatusOrderByIdDesc(status, pageable);
        return PageResponse.from(page, CourseRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public CourseRequestResponse get(Long id) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        CourseRequest request = requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course request", id));
        boolean mine = me.isStudent() && request.getStudentId().equals(me.profileId());
        if (!mine && !me.isStaff()) {
            throw new ResourceNotFoundException("Course request", id);
        }
        return CourseRequestResponse.from(request);
    }

    @Transactional
    public CourseRequestResponse approve(Long id, ApproveRequest body) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        requireStaff(me);
        CourseRequest request = requests.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course request", id));
        requireOpen(request);

        Long batchId = body.batchId() != null ? body.batchId() : request.getPreferredBatchId();
        if (batchId == null) {
            throw new BusinessRuleException("Choose the batch to place this student in.");
        }
        Batch batch = batches.findById(batchId).orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));
        if (!batch.getCourseId().equals(request.getCourseId())) {
            throw new BusinessRuleException("That batch belongs to a different course.");
        }

        EnrollmentResultResponse result = batchService.enroll(batchId, new EnrollStudentRequest(List.of(request.getStudentId()), request.getCourseId()));
        if (result.enrolled() == 0) {
            String reason = result.outcomes().stream().findFirst().map(EnrollmentResultResponse.Outcome::message)
                    .orElse("The student could not be enrolled.");
            throw new BusinessRuleException(reason);
        }

        request.setStatus(CourseRequestStatus.APPROVED);
        request.setApprovedBatchId(batchId);
        request.setEnrollmentId(enrollments
                .findByStudentIdAndBatchIdAndStatus(request.getStudentId(), batchId, EnrollmentStatus.ACTIVE)
                .map(e -> e.getId()).orElse(null));
        stampDecision(request, me, body.note());

        events.audit(SERVICE_NAME, "COURSE_REQUEST_APPROVED", "CourseRequest", id, null,
                Map.of("studentId", request.getStudentId(), "batchId", batchId));
        return CourseRequestResponse.from(request);
    }

    @Transactional
    public CourseRequestResponse reject(Long id, RejectRequest body) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        requireStaff(me);
        CourseRequest request = requests.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course request", id));
        requireOpen(request);

        request.setStatus(CourseRequestStatus.REJECTED);
        stampDecision(request, me, body.note());

        events.notifyUsers(List.of(request.getUserId()), "COURSE_REQUEST",
                "Your request for " + request.getCourseTitle() + " was not approved",
                body.note().trim(), "/course-requests");
        events.audit(SERVICE_NAME, "COURSE_REQUEST_REJECTED", "CourseRequest", id, null,
                Map.of("studentId", request.getStudentId()));
        return CourseRequestResponse.from(request);
    }

    // -----------------------------------------------------------------
    // Online purchase: a confirmed payment is the approval
    // -----------------------------------------------------------------

    /** The first batch of the course that still takes students and has a free seat, preferring {@code preferredBatchId}. */
    @Transactional(readOnly = true)
    public java.util.Optional<OpenBatchResponse> openBatch(Long courseId, Long preferredBatchId) {
        List<Batch> candidates = new java.util.ArrayList<>(batches.findByCourseIdAndStatusInOrderByStartDateAsc(
                courseId, List.of(com.itilms.batch.entity.BatchStatus.PLANNED, com.itilms.batch.entity.BatchStatus.ONGOING)));
        if (preferredBatchId != null) {
            // Stable sort: the preferred batch first, the rest still earliest start first.
            candidates.sort(java.util.Comparator.comparing(b -> !b.getId().equals(preferredBatchId)));
        }
        for (Batch batch : candidates) {
            long left = batch.getCapacity() - enrollments.countByBatchIdAndStatus(batch.getId(), EnrollmentStatus.ACTIVE);
            if (left > 0) {
                return java.util.Optional.of(new OpenBatchResponse(batch.getId(), batch.getBatchCode(), left));
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Places a student who paid online. The payment is confirmed with finance-service (as the student, so it
     * answers only for their own order); nothing the browser says is trusted.
     */
    @Transactional
    public PaidJoinResponse paidJoin(PaidJoinRequest request) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        if (!me.isStudent() || me.profileId() == null) {
            throw new ForbiddenOperationException("Only a student can join a course.");
        }
        Long studentId = me.profileId();
        FinanceClient.OnlineOrder order = financeClient.order(request.orderId());
        if (order == null || !order.paid() || !studentId.equals(order.studentId())
                || !request.courseId().equals(order.courseId())) {
            throw new BusinessRuleException("This payment could not be confirmed for this course.");
        }

        if (order.wholeBatch()) {
            return paidJoinWholeBatch(me, studentId, order, request);
        }

        var active = enrollments.findActiveForStudent(studentId).stream()
                .filter(e -> request.courseId().equals(e.getCourseId())).findFirst();
        if (active.isPresent()) {
            return new PaidJoinResponse(active.get().getBatchId(), null, true);
        }

        OpenBatchResponse open = openBatch(request.courseId(), order.batchId()).orElse(null);
        if (open == null) {
            String who = me.email() + " paid for course " + request.courseId() + " but no batch has a free seat";
            events.notifyRole(Roles.ADMIN, "COURSE_REQUEST", "Paid student needs a batch", who, "/batches");
            events.notifyRole(Roles.COORDINATOR, "COURSE_REQUEST", "Paid student needs a batch", who, "/batches");
            throw new BusinessRuleException(
                    "Your payment is safe, but no batch has a free seat right now. The institute has been told and will place you.");
        }
        EnrollmentResultResponse result = batchService.enroll(open.batchId(), new EnrollStudentRequest(List.of(studentId), request.courseId()));
        if (result.enrolled() == 0) {
            throw new BusinessRuleException(result.outcomes().stream().findFirst()
                    .map(EnrollmentResultResponse.Outcome::message).orElse("You could not be placed in the batch."));
        }
        events.audit(SERVICE_NAME, "COURSE_JOINED_AFTER_PAYMENT", "Batch", open.batchId(), null,
                Map.of("studentId", studentId, "orderId", request.orderId()));
        return new PaidJoinResponse(open.batchId(), open.batchCode(), false);
    }

    /**
     * The student bought a whole batch: place them in exactly that batch, with every course in it, at the batch fee
     * they paid. If the seat went in the meantime nothing is lost: the order stays paid and the institute is told.
     */
    private PaidJoinResponse paidJoinWholeBatch(AppPrincipal me, Long studentId, FinanceClient.OnlineOrder order,
                                                PaidJoinRequest request) {
        Long batchId = order.batchId();
        Batch batch = batchId == null ? null : batches.findById(batchId).orElse(null);
        if (batch == null || !batch.getCourseId().equals(request.courseId())) {
            throw new BusinessRuleException("This payment could not be confirmed for this batch.");
        }
        if (enrollments.existsByStudentIdAndBatchIdAndStatus(studentId, batchId, EnrollmentStatus.ACTIVE)) {
            return new PaidJoinResponse(batchId, batch.getBatchCode(), true);
        }
        EnrollmentResultResponse result = batchService.enroll(batchId, new EnrollStudentRequest(List.of(studentId)));
        if (result.enrolled() == 0) {
            String who = me.email() + " paid for batch " + batch.getBatchCode() + " but could not be placed in it";
            events.notifyRole(Roles.ADMIN, "COURSE_REQUEST", "Paid student needs a place", who, "/batches");
            events.notifyRole(Roles.COORDINATOR, "COURSE_REQUEST", "Paid student needs a place", who, "/batches");
            throw new BusinessRuleException(result.outcomes().stream().findFirst()
                    .map(EnrollmentResultResponse.Outcome::message).orElse("You could not be placed in the batch.")
                    + " Your payment is safe; the institute has been told and will place you.");
        }
        events.audit(SERVICE_NAME, "BATCH_JOINED_AFTER_PAYMENT", "Batch", batchId, null,
                Map.of("studentId", studentId, "orderId", request.orderId()));
        return new PaidJoinResponse(batchId, batch.getBatchCode(), false);
    }

    // -----------------------------------------------------------------

    private static void requireStaff(AppPrincipal me) {
        if (!me.isStaff()) {
            throw new ForbiddenOperationException("Only an administrator or coordinator can decide a course request.");
        }
    }

    private static void requireOpen(CourseRequest request) {
        if (!request.getStatus().isOpen()) {
            throw new BusinessRuleException("This request has already been " + request.getStatus().name().toLowerCase() + ".");
        }
    }

    private static void stampDecision(CourseRequest request, AppPrincipal me, String note) {
        request.setDecidedBy(me.userId());
        request.setDecidedByName(me.fullName());
        request.setDecidedAt(Instant.now());
        request.setDecisionNote(blankToNull(note));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
