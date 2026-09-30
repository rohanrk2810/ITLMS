package com.itilms.certificate.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.certificate.client.AdmissionClient;
import com.itilms.certificate.client.BatchClient;
import com.itilms.certificate.client.CourseClient;
import com.itilms.certificate.config.CertificateProperties;
import com.itilms.certificate.dto.response.EligibilityResponse;
import com.itilms.certificate.dto.response.EligibilityResponse.Criterion;
import com.itilms.certificate.entity.CertificateRequest;
import com.itilms.certificate.entity.CertificateRequestStatus;
import com.itilms.certificate.repository.CertificateRepository;
import com.itilms.certificate.repository.CertificateRequestRepository;
import com.itilms.certificate.service.CertificateService;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.security.AppPrincipal;

@ExtendWith(MockitoExtension.class)
class CertificateRequestServiceImplTest {

    private static final long STUDENT = 30L;
    private static final long COURSE = 5L;

    @Mock CertificateRequestRepository requests;
    @Mock CertificateRepository certificates;
    @Mock CertificateService certificateService;
    @Mock CourseClient courseClient;
    @Mock BatchClient batchClient;
    @Mock AdmissionClient admissionClient;
    @Mock EventPublisher events;

    CertificateProperties props = new CertificateProperties();
    CertificateRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CertificateRequestServiceImpl(requests, certificates, certificateService, courseClient,
                batchClient, admissionClient, props, events, new ObjectMapper());
        lenient().when(requests.save(any(CertificateRequest.class))).thenAnswer(i -> i.getArgument(0));
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

    private static EligibilityResponse eligibility(boolean eligible) {
        return new EligibilityResponse(STUDENT, COURSE, 9L, eligible,
                List.of(eligible ? Criterion.met("attendance", "Attendance", "90%")
                        : Criterion.notMet("attendance", "Attendance", "40%")),
                List.of(), null);
    }

    private static CertificateRequest pending() {
        return CertificateRequest.builder().id(1L).studentId(STUDENT).studentUserId(7L).studentName("Asha")
                .courseId(COURSE).courseTitle("Java").build();
    }

    private void stubHappyRequest() {
        when(certificateService.eligibility(STUDENT, COURSE)).thenReturn(eligibility(true));
        when(admissionClient.lookup(List.of(STUDENT)))
                .thenReturn(List.of(new AdmissionClient.Student(STUDENT, 7L, "STU-1", "Asha Patil", "ACTIVE")));
        when(courseClient.course(COURSE)).thenReturn(
                new CourseClient.CourseDetail(new CourseClient.CourseSummary(COURSE, "Java", "JV")));
        when(batchClient.batch(9L)).thenReturn(new BatchClient.BatchInfo(9L, "B-9", "Morning Batch"));
    }

    // ------------------------------------------------------------------ student

    @Test
    void eligibleStudentCreatesAPendingRequestWithTheBatchFromTheirEnrolment() {
        signInAs("STUDENT", STUDENT);
        stubHappyRequest();

        var response = service.request(COURSE);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.batchId()).isEqualTo(9L);
        assertThat(response.batchName()).isEqualTo("Morning Batch");
        assertThat(response.studentName()).isEqualTo("Asha Patil");
    }

    @Test
    void ineligibleStudentIsRefusedAndNothingIsSaved() {
        signInAs("STUDENT", STUDENT);
        when(certificateService.eligibility(STUDENT, COURSE)).thenReturn(eligibility(false));

        assertThatThrownBy(() -> service.request(COURSE))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Attendance");
        verify(requests, never()).save(any());
    }

    @Test
    void secondOpenRequestForTheSameCourseIsRefused() {
        signInAs("STUDENT", STUDENT);
        when(requests.existsByStudentIdAndCourseIdAndStatusIn(eq(STUDENT), eq(COURSE), any())).thenReturn(true);

        assertThatThrownBy(() -> service.request(COURSE)).isInstanceOf(DuplicateResourceException.class);
        verify(requests, never()).save(any());
    }

    @Test
    void studentWhoAlreadyHoldsACertificateCannotRequestAnother() {
        signInAs("STUDENT", STUDENT);
        when(certificates.existsByStudentIdAndCourseIdAndStatus(eq(STUDENT), eq(COURSE), any())).thenReturn(true);

        assertThatThrownBy(() -> service.request(COURSE)).isInstanceOf(DuplicateResourceException.class);
    }

    // ------------------------------------------------------------------ decisions

    @Test
    void adminApprovalMovesPendingToApproved() {
        signInAs("ADMIN", null);
        when(requests.findById(1L)).thenReturn(Optional.of(pending()));

        var response = service.approve(1L);

        assertThat(response.status()).isEqualTo("APPROVED");
    }

    @Test
    void onlyAPendingRequestCanBeApproved() {
        signInAs("ADMIN", null);
        CertificateRequest done = pending();
        done.reject(1L, "no", java.time.Instant.now());
        when(requests.findById(1L)).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> service.approve(1L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectionNeedsAReasonAndKeepsIt() {
        signInAs("ADMIN", null);
        when(requests.findById(1L)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.reject(1L, "  ")).isInstanceOf(BusinessRuleException.class);

        var response = service.reject(1L, "Course completion requirements are not yet satisfied.");
        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.rejectionReason()).startsWith("Course completion");
    }

    @Test
    void aStudentCannotApproveRejectOrIssue() {
        signInAs("STUDENT", STUDENT);

        assertThatThrownBy(() -> service.approve(1L)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.reject(1L, "x")).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.issue(1L)).isInstanceOf(ForbiddenOperationException.class);
        verify(certificateService, never()).issue(anyLong(), anyLong());
    }

    @Test
    void aTrainerCannotDecideEvenWhenAllowedToLook() {
        props.setTrainerAccess(true);
        signInAs("TRAINER", 3L);

        assertThatThrownBy(() -> service.approve(1L)).isInstanceOf(ForbiddenOperationException.class);
    }

    // ------------------------------------------------------------------ issuing

    @Test
    void issueNeedsAnApprovedRequest() {
        signInAs("ADMIN", null);
        when(requests.findById(1L)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.issue(1L)).isInstanceOf(BusinessRuleException.class);
        verify(certificateService, never()).issue(anyLong(), anyLong());
    }

    @Test
    void issueOfAnApprovedRequestGoesThroughTheCertificateIssuePath() {
        signInAs("ADMIN", null);
        CertificateRequest approved = pending();
        approved.approve(7L, java.time.Instant.now());
        when(requests.findById(1L)).thenReturn(Optional.of(approved));

        service.issue(1L);

        verify(certificateService).issue(STUDENT, COURSE);
        assertThat(approved.getStatus()).as("the certificate service marks it ISSUED in the same transaction")
                .isEqualTo(CertificateRequestStatus.APPROVED);
    }

    // ------------------------------------------------------------------ who may look

    @Test
    void trainerSeesNothingUnlessTheInstituteSwitchedItOn() {
        signInAs("TRAINER", 3L);

        assertThatThrownBy(() -> service.search(null, null, null, null, null, null,
                org.springframework.data.domain.Pageable.unpaged()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void studentCannotOpenSomeoneElsesRequest() {
        signInAs("STUDENT", 99L);
        when(requests.findById(1L)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(ForbiddenOperationException.class);
    }
}
