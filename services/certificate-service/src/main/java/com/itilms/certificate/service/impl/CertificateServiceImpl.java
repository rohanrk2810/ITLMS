package com.itilms.certificate.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.certificate.client.AdmissionClient;
import com.itilms.certificate.client.AssessmentClient;
import com.itilms.certificate.client.BatchClient;
import com.itilms.certificate.client.CourseClient;
import com.itilms.certificate.client.FinanceClient;
import com.itilms.certificate.config.CertificateProperties;
import com.itilms.certificate.dto.response.CertificateResponse;
import com.itilms.certificate.dto.response.EligibilityResponse;
import com.itilms.certificate.dto.response.VerificationResponse;
import com.itilms.certificate.entity.Certificate;
import com.itilms.certificate.entity.CertificateStatus;
import com.itilms.certificate.repository.CertificateRepository;
import com.itilms.certificate.service.CertificatePdfRenderer;
import com.itilms.certificate.service.CertificateService;
import com.itilms.certificate.service.EligibilityRules;
import com.itilms.certificate.service.VerificationCodes;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.CertificateIssuedEvent;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.NotificationRequestedEvent;
import com.itilms.common.exception.ApiException;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.common.util.Codes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateServiceImpl implements CertificateService {

    private static final String SERVICE_NAME = "certificate-service";

    private final CertificateRepository certificateRepository;
    private final CourseClient courseClient;
    private final AssessmentClient assessmentClient;
    private final BatchClient batchClient;
    private final FinanceClient financeClient;
    private final AdmissionClient admissionClient;
    private final CertificatePdfRenderer pdfRenderer;
    private final CertificateProperties props;
    private final EventPublisher events;
    private final ObjectMapper objectMapper;

    // -----------------------------------------------------------------
    // Eligibility
    // -----------------------------------------------------------------

    @Override
    public EligibilityResponse eligibility(Long studentId, Long courseId) {
        SecurityUtils.requireStudentOwnershipOrStaff(studentId);
        EligibilityRules.Facts facts = gather(studentId, courseId);
        List<EligibilityResponse.Criterion> criteria =
                EligibilityRules.evaluate(facts, props.getCriteria(), props.getCurrencySymbol());

        String existing = certificateRepository.findByStudentIdOrderByIssueDateDesc(studentId).stream()
                .filter(c -> c.getCourseId().equals(courseId) && c.isValid())
                .map(Certificate::getCertificateNo).findFirst().orElse(null);

        return new EligibilityResponse(studentId, courseId,
                facts.progress() == null ? null : facts.progress().batchId(),
                EligibilityRules.eligible(criteria), criteria,
                facts.completion() == null ? List.of() : facts.completion().outstanding(),
                existing);
    }

    /**
     * Asks each owning service for its fact.
     *
     * <p>The batch comes from the student's own enrolment, via their course
     * progress - never from the request - so the attendance and assessment
     * conditions are judged in the batch the student actually studied in.
     */
    private EligibilityRules.Facts gather(Long studentId, Long courseId) {
        CourseClient.Progress progress = courseClient.progress(studentId, courseId);
        Long batchId = progress == null ? null : progress.batchId();

        AssessmentClient.Completion completion = batchId == null ? null
                : assessmentClient.completion(studentId, courseId, batchId);
        BatchClient.AttendanceSummary attendance = batchId == null ? null
                : batchClient.attendance(studentId, batchId);
        List<FinanceClient.FeePlan> plans = financeClient.plans(studentId);

        return new EligibilityRules.Facts(progress, completion, attendance, plans, courseId);
    }

    // -----------------------------------------------------------------
    // Issuing
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public CertificateResponse issue(Long studentId, Long courseId) {
        if (studentId == null) {
            throw new BusinessRuleException("Choose the student to issue the certificate to.");
        }
        AppPrincipal issuer = SecurityUtils.requirePrincipal();
        if (certificateRepository.existsByStudentIdAndCourseIdAndStatus(studentId, courseId, CertificateStatus.ISSUED)) {
            throw new DuplicateResourceException("This student already holds a certificate for this course.");
        }

        // Checked again here, not trusted from an earlier eligibility screen:
        // a payment can be reversed between the two.
        EligibilityRules.Facts facts = gather(studentId, courseId);
        List<EligibilityResponse.Criterion> criteria =
                EligibilityRules.evaluate(facts, props.getCriteria(), props.getCurrencySymbol());
        if (!EligibilityRules.eligible(criteria)) {
            String unmet = criteria.stream()
                    .filter(c -> c.outcome() == EligibilityResponse.Outcome.NOT_MET
                            || c.outcome() == EligibilityResponse.Outcome.UNAVAILABLE)
                    .map(c -> c.name() + (c.detail() == null ? "" : " (" + c.detail() + ")"))
                    .reduce((a, b) -> a + "; " + b).orElse("");
            throw new BusinessRuleException("NOT_ELIGIBLE", "Not yet eligible: " + unmet);
        }

        AdmissionClient.Student student = admissionClient.lookup(List.of(studentId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        CourseClient.CourseDetail course = courseClient.course(courseId);
        if (course == null || course.course() == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "COURSE_UNAVAILABLE",
                    "The course could not be read to print its title. Please try again shortly.");
        }

        Certificate certificate = certificateRepository.save(Certificate.builder()
                .certificateNo(Codes.certificateNo(certificateRepository.nextSequence()))
                .verificationCode(VerificationCodes.generate())
                .studentId(studentId)
                .studentUserId(student.userId())
                .studentCode(student.studentCode())
                .studentName(student.fullName())
                .courseId(courseId)
                .courseTitle(course.course().title())
                .batchId(facts.progress().batchId())
                .issueDate(LocalDate.now(props.getZone()))
                .completionDate(LocalDate.now(props.getZone()))
                .status(CertificateStatus.ISSUED)
                .evidence(toJson(criteria))
                .issuedBy(issuer.userId())
                .build());

        String url = verificationUrl(certificate);
        events.publishAfterCommit(KafkaTopics.CERTIFICATE_ISSUED, String.valueOf(studentId),
                new CertificateIssuedEvent(DomainEvent.newId(), Instant.now(), certificate.getId(),
                        certificate.getCertificateNo(), studentId, student.userId(), courseId,
                        certificate.getCourseTitle(), certificate.getIssueDate(), url));
        if (student.userId() != null) {
            // Doc S16: certificate issued - in-app and email.
            events.publishAfterCommit(KafkaTopics.NOTIFICATION_REQUESTED,
                    NotificationRequestedEvent.toUsersWithEmail(List.of(student.userId()), "CERTIFICATE",
                            "Your certificate is ready",
                            "Congratulations on completing %s. Certificate no. %s."
                                    .formatted(certificate.getCourseTitle(), certificate.getCertificateNo()),
                            "/student/certificates"));
        }
        events.audit(SERVICE_NAME, "CERTIFICATE_ISSUED", "Certificate", certificate.getId(), null,
                Map.of("certificateNo", certificate.getCertificateNo(), "studentId", studentId,
                        "courseId", courseId, "selfClaimed", issuer.isStudent()));

        log.info("Issued certificate {} to student {} for course {}", certificate.getCertificateNo(), studentId, courseId);
        return CertificateResponse.from(certificate, url);
    }

    @Override
    @Transactional
    public CertificateResponse claim(Long courseId) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.studentIdOrNull() == null) {
            throw new ForbiddenOperationException("Only a student with a linked profile can claim a certificate.");
        }
        return issue(caller.profileId(), courseId);
    }

    // -----------------------------------------------------------------
    // Reading and revoking
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CertificateResponse> mine() {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.studentIdOrNull() == null) {
            throw new ForbiddenOperationException("Your account is not linked to a student profile yet.");
        }
        return certificateRepository.findByStudentIdOrderByIssueDateDesc(caller.profileId()).stream()
                .map(c -> CertificateResponse.from(c, verificationUrl(c))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateResponse get(Long id) {
        Certificate certificate = require(id);
        SecurityUtils.requireStudentOwnershipOrStaff(certificate.getStudentId());
        return CertificateResponse.from(certificate, verificationUrl(certificate));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CertificateResponse> list(Long courseId, Pageable pageable) {
        var page = courseId == null
                ? certificateRepository.findAllByOrderByIssueDateDesc(pageable)
                : certificateRepository.findByCourseIdOrderByIssueDateDesc(courseId, pageable);
        return PageResponse.from(page, c -> CertificateResponse.from(c, verificationUrl(c)));
    }

    @Override
    @Transactional
    public CertificateResponse revoke(Long id, String reason) {
        AppPrincipal admin = SecurityUtils.requirePrincipal();
        Certificate certificate = require(id);
        if (!certificate.isValid()) {
            throw new BusinessRuleException("This certificate has already been revoked.");
        }
        certificate.revoke(reason.trim(), admin.userId(), Instant.now());
        certificateRepository.save(certificate);

        if (certificate.getStudentUserId() != null) {
            events.notifyUsers(List.of(certificate.getStudentUserId()), "CERTIFICATE",
                    "Certificate revoked",
                    "Certificate %s for %s has been revoked: %s".formatted(certificate.getCertificateNo(),
                            certificate.getCourseTitle(), reason.trim()),
                    "/student/certificates");
        }
        events.audit(SERVICE_NAME, "CERTIFICATE_REVOKED", "Certificate", id,
                Map.of("status", "ISSUED"), Map.of("status", "REVOKED", "reason", reason.trim()));
        return CertificateResponse.from(certificate, verificationUrl(certificate));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] pdf(Long id) {
        Certificate certificate = require(id);
        SecurityUtils.requireStudentOwnershipOrStaff(certificate.getStudentId());
        if (!certificate.isValid()) {
            throw new BusinessRuleException("This certificate has been revoked and cannot be downloaded.");
        }
        return pdfRenderer.render(certificate, verificationUrl(certificate));
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationResponse verify(String certificateNo, String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessRuleException("VERIFICATION_CODE_REQUIRED",
                    "Enter the verification code printed beside the certificate number.");
        }
        Certificate certificate = certificateRepository.findByCertificateNo(certificateNo.trim().toUpperCase())
                .filter(c -> VerificationCodes.matches(c.getVerificationCode(), code))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No certificate matches that number and verification code."));

        return new VerificationResponse(certificate.getCertificateNo(),
                certificate.isValid() ? "VALID" : "REVOKED",
                certificate.getStudentName(), certificate.getCourseTitle(),
                certificate.getIssueDate(), props.getInstituteName());
    }

    // -----------------------------------------------------------------

    private String verificationUrl(Certificate c) {
        return "%s/%s?code=%s".formatted(props.getVerificationBaseUrl(), c.getCertificateNo(), c.getVerificationCode());
    }

    private Certificate require(Long id) {
        return certificateRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Certificate", id));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
