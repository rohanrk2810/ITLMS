package com.itilms.certificate.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.certificate.client.AdmissionClient;
import com.itilms.certificate.client.BatchClient;
import com.itilms.certificate.client.CourseClient;
import com.itilms.certificate.config.CertificateProperties;
import com.itilms.certificate.dto.response.CertificateRequestDetailResponse;
import com.itilms.certificate.dto.response.CertificateRequestResponse;
import com.itilms.certificate.dto.response.CertificateResponse;
import com.itilms.certificate.dto.response.EligibilityResponse;
import com.itilms.certificate.entity.Certificate;
import com.itilms.certificate.entity.CertificateRequest;
import com.itilms.certificate.entity.CertificateRequestStatus;
import com.itilms.certificate.entity.CertificateStatus;
import com.itilms.certificate.repository.CertificateRepository;
import com.itilms.certificate.repository.CertificateRequestRepository;
import com.itilms.certificate.service.CertificateRequestService;
import com.itilms.certificate.service.CertificateService;
import com.itilms.certificate.service.EligibilityRules;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.NotificationRequestedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateRequestServiceImpl implements CertificateRequestService {

    private static final String SERVICE_NAME = "certificate-service";
    private static final List<CertificateRequestStatus> OPEN =
            List.of(CertificateRequestStatus.PENDING, CertificateRequestStatus.APPROVED);
    private static final String STUDENT_LINK = "/student/certificates";

    private final CertificateRequestRepository requests;
    private final CertificateRepository certificates;
    private final CertificateService certificateService;
    private final CourseClient courseClient;
    private final BatchClient batchClient;
    private final AdmissionClient admissionClient;
    private final CertificateProperties props;
    private final EventPublisher events;
    private final ObjectMapper objectMapper;

    // -----------------------------------------------------------------
    // Student
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public CertificateRequestResponse request(Long courseId) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.studentIdOrNull() == null) {
            throw new ForbiddenOperationException("Only a student with a linked profile can request a certificate.");
        }
        Long studentId = caller.profileId();

        if (certificates.existsByStudentIdAndCourseIdAndStatus(studentId, courseId, CertificateStatus.ISSUED)) {
            throw new DuplicateResourceException("You already hold a certificate for this course.");
        }
        if (requests.existsByStudentIdAndCourseIdAndStatusIn(studentId, courseId, OPEN)) {
            throw new DuplicateResourceException("You already have a certificate request open for this course.");
        }

        // The same checks, against the same services, that an admin's issue will repeat.
        EligibilityResponse eligibility = certificateService.eligibility(studentId, courseId);
        if (!eligibility.eligible()) {
            throw new BusinessRuleException("NOT_ELIGIBLE",
                    "Not yet eligible: " + EligibilityRules.unmetSummary(eligibility.criteria()));
        }

        AdmissionClient.Student student = admissionClient.lookup(List.of(studentId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        CourseClient.CourseDetail course = courseClient.course(courseId);
        if (course == null || course.course() == null) {
            throw new BusinessRuleException("COURSE_UNAVAILABLE",
                    "The course could not be read. Please try again shortly.");
        }
        Long batchId = eligibility.batchId();
        BatchClient.BatchInfo batch = batchId == null ? null : batchClient.batch(batchId);

        CertificateRequest saved = requests.save(CertificateRequest.builder()
                .studentId(studentId)
                .studentUserId(student.userId())
                .studentCode(student.studentCode())
                .studentName(student.fullName())
                .courseId(courseId)
                .courseTitle(course.course().title())
                .batchId(batchId)
                .batchName(batch == null ? null : batch.name())
                .eligibilitySnapshot(toJson(eligibility.criteria()))
                .build());

        events.publishAfterCommit(KafkaTopics.NOTIFICATION_REQUESTED,
                NotificationRequestedEvent.toUsers(List.of(caller.userId()), "CERTIFICATE",
                        "Certificate request submitted",
                        "Certificate request submitted successfully for %s.".formatted(saved.getCourseTitle()),
                        STUDENT_LINK));
        events.publishAfterCommit(KafkaTopics.NOTIFICATION_REQUESTED,
                NotificationRequestedEvent.toRole("ADMIN", "CERTIFICATE", "New certificate request",
                        "%s has requested a certificate for %s.".formatted(saved.getStudentName(),
                                saved.getCourseTitle()),
                        STUDENT_LINK));
        audit("CERTIFICATE_REQUESTED", saved, null);
        log.info("Student {} requested a certificate for course {}", studentId, courseId);
        return CertificateRequestResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateRequestResponse> mine() {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.studentIdOrNull() == null) {
            throw new ForbiddenOperationException("Your account is not linked to a student profile yet.");
        }
        return requests.findByStudentIdOrderByRequestedAtDesc(caller.profileId()).stream()
                .map(CertificateRequestResponse::from).toList();
    }

    // -----------------------------------------------------------------
    // Review
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CertificateRequestResponse> search(List<CertificateRequestStatus> statuses, Long courseId,
                                                           Long batchId, String query, LocalDate from, LocalDate to,
                                                           Pageable pageable) {
        requireReviewer();
        var zone = props.getZone();
        String q = query == null || query.isBlank() ? "%" : "%" + query.trim().toLowerCase() + "%";
        var page = requests.search(
                statuses == null || statuses.isEmpty() ? List.of(CertificateRequestStatus.values()) : statuses,
                courseId == null ? 0 : courseId, batchId == null ? 0 : batchId,
                from == null ? Instant.EPOCH : from.atStartOfDay(zone).toInstant(),
                to == null ? Instant.parse("9999-01-01T00:00:00Z") : to.plusDays(1).atStartOfDay(zone).toInstant(),
                q, pageable);
        return PageResponse.from(page, CertificateRequestResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateRequestDetailResponse get(Long id) {
        CertificateRequest request = require(id);
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.isStudent()) {
            if (!request.getStudentId().equals(caller.profileId())) {
                throw new ForbiddenOperationException("You may only access your own records");
            }
            return new CertificateRequestDetailResponse(CertificateRequestResponse.from(request), null);
        }
        requireReviewer();
        // A trainer, when allowed at all, sees the request but does not run the eligibility checks.
        EligibilityResponse eligibility = caller.isTrainer() ? null
                : certificateService.eligibility(request.getStudentId(), request.getCourseId());
        return new CertificateRequestDetailResponse(CertificateRequestResponse.from(request), eligibility);
    }

    @Override
    @Transactional
    public CertificateRequestResponse approve(Long id) {
        AppPrincipal admin = requireAdmin();
        CertificateRequest request = require(id);
        if (request.getStatus() != CertificateRequestStatus.PENDING) {
            throw new BusinessRuleException("Only a pending request can be approved; this one is "
                    + request.getStatus().name().toLowerCase() + ".");
        }
        request.approve(admin.userId(), Instant.now());
        requests.save(request);

        notifyStudent(request, "Certificate request approved",
                "Your certificate request for %s has been approved by the administrator."
                        .formatted(request.getCourseTitle()));
        audit("CERTIFICATE_APPROVED", request, Map.of("reviewedBy", admin.userId()));
        return CertificateRequestResponse.from(request);
    }

    @Override
    @Transactional
    public CertificateRequestResponse reject(Long id, String reason) {
        AppPrincipal admin = requireAdmin();
        CertificateRequest request = require(id);
        if (!request.getStatus().isOpen()) {
            throw new BusinessRuleException("This request is already " + request.getStatus().name().toLowerCase() + ".");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("A reason is required to reject a request.");
        }
        request.reject(admin.userId(), reason.trim(), Instant.now());
        requests.save(request);

        notifyStudent(request, "Certificate request rejected",
                "Your certificate request for %s has been rejected. Reason: %s"
                        .formatted(request.getCourseTitle(), reason.trim()));
        audit("CERTIFICATE_REJECTED", request, Map.of("reason", reason.trim(), "reviewedBy", admin.userId()));
        return CertificateRequestResponse.from(request);
    }

    @Override
    @Transactional
    public CertificateResponse issue(Long id) {
        requireAdmin();
        CertificateRequest request = require(id);
        if (request.getStatus() != CertificateRequestStatus.APPROVED) {
            throw new BusinessRuleException("Approve the request before issuing its certificate; this one is "
                    + request.getStatus().name().toLowerCase() + ".");
        }
        // Re-checks eligibility, writes the certificate, proves the PDF renders, and marks this
        // request ISSUED, all in this one transaction. Any failure leaves the request APPROVED.
        return certificateService.issue(request.getStudentId(), request.getCourseId());
    }

    // -----------------------------------------------------------------

    private AppPrincipal requireAdmin() {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (!caller.isAdmin()) {
            throw new ForbiddenOperationException("Only an administrator can decide certificate requests.");
        }
        return caller;
    }

    /** Admins always; trainers only when the institute has switched that on; nobody else. */
    private void requireReviewer() {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        boolean allowed = caller.isAdmin() || (caller.isTrainer() && props.isTrainerAccess());
        if (!allowed) {
            throw new ForbiddenOperationException("You do not have access to certificate requests.");
        }
    }

    private CertificateRequest require(Long id) {
        return requests.findById(id).orElseThrow(() -> new ResourceNotFoundException("Certificate request", id));
    }

    private void notifyStudent(CertificateRequest request, String title, String message) {
        if (request.getStudentUserId() != null) {
            events.publishAfterCommit(KafkaTopics.NOTIFICATION_REQUESTED,
                    NotificationRequestedEvent.toUsersWithEmail(List.of(request.getStudentUserId()), "CERTIFICATE",
                            title, message, STUDENT_LINK));
        }
    }

    private void audit(String action, CertificateRequest request, Map<String, Object> extra) {
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("studentId", request.getStudentId());
        details.put("courseId", request.getCourseId());
        details.put("status", request.getStatus().name());
        if (extra != null) {
            details.putAll(extra);
        }
        events.audit(SERVICE_NAME, action, "CertificateRequest", request.getId(), null, details);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
