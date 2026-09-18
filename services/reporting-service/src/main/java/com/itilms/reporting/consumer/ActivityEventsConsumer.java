package com.itilms.reporting.consumer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.ApplicationStageChangedEvent;
import com.itilms.common.event.CertificateIssuedEvent;
import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.common.event.FeePlanCreatedEvent;
import com.itilms.common.event.InstallmentOverdueEvent;
import com.itilms.common.event.JobPostedEvent;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.PaymentRecordedEvent;
import com.itilms.common.event.QuizAttemptCompletedEvent;
import com.itilms.common.event.StudentAdmittedEvent;
import com.itilms.common.event.SubmissionEvaluatedEvent;
import com.itilms.reporting.repository.ActivityEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * The facts the dashboard is built from (Doc S15). Each listener maps one
 * event onto a metric key; {@link com.itilms.reporting.service.impl.DashboardServiceImpl}
 * groups {@code activity_events} by that key rather than any service keeping
 * a running counter, so a redelivered event cannot inflate a total.
 */
@Component
@RequiredArgsConstructor
public class ActivityEventsConsumer {

    private static final String GROUP = "reporting-service";

    private final ActivityEventRepository repository;

    @KafkaListener(topics = KafkaTopics.STUDENT_ADMITTED, groupId = GROUP)
    @Transactional
    public void onStudentAdmitted(StudentAdmittedEvent event) {
        record(event.eventId(), "STUDENT_ADMITTED", event.occurredAt(), null, event.studentId(), null);
    }

    @KafkaListener(topics = KafkaTopics.ENROLLMENT_CREATED, groupId = GROUP)
    @Transactional
    public void onEnrollmentCreated(EnrollmentCreatedEvent event) {
        record(event.eventId(), "ENROLLMENT_CREATED", event.occurredAt(), null, event.studentId(), null);
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_RECORDED, groupId = GROUP)
    @Transactional
    public void onPaymentRecorded(PaymentRecordedEvent event) {
        record(event.eventId(), "PAYMENT_RECORDED", event.occurredAt(), event.amount(),
                event.studentId(), event.method());
    }

    @KafkaListener(topics = KafkaTopics.FEE_PLAN_CREATED, groupId = GROUP)
    @Transactional
    public void onFeePlanCreated(FeePlanCreatedEvent event) {
        record(event.eventId(), "FEE_PLAN_CREATED", event.occurredAt(), event.netFee(), event.studentId(), null);
    }

    @KafkaListener(topics = KafkaTopics.INSTALLMENT_OVERDUE, groupId = GROUP)
    @Transactional
    public void onInstallmentOverdue(InstallmentOverdueEvent event) {
        record(event.eventId(), "INSTALLMENT_OVERDUE", event.occurredAt(), event.amountOverdue(),
                event.studentId(), null);
    }

    @KafkaListener(topics = KafkaTopics.SUBMISSION_EVALUATED, groupId = GROUP)
    @Transactional
    public void onSubmissionEvaluated(SubmissionEvaluatedEvent event) {
        record(event.eventId(), "SUBMISSION_EVALUATED", event.occurredAt(), null,
                event.studentId(), event.status());
    }

    @KafkaListener(topics = KafkaTopics.QUIZ_ATTEMPT_COMPLETED, groupId = GROUP)
    @Transactional
    public void onQuizAttemptCompleted(QuizAttemptCompletedEvent event) {
        record(event.eventId(), "QUIZ_ATTEMPT_COMPLETED", event.occurredAt(), null,
                event.studentId(), event.passed() ? "PASSED" : "FAILED");
    }

    @KafkaListener(topics = KafkaTopics.CERTIFICATE_ISSUED, groupId = GROUP)
    @Transactional
    public void onCertificateIssued(CertificateIssuedEvent event) {
        record(event.eventId(), "CERTIFICATE_ISSUED", event.occurredAt(), null, event.studentId(), null);
    }

    @KafkaListener(topics = KafkaTopics.JOB_POSTED, groupId = GROUP)
    @Transactional
    public void onJobPosted(JobPostedEvent event) {
        record(event.eventId(), "JOB_POSTED", event.occurredAt(), null, event.jobId(), null);
    }

    @KafkaListener(topics = KafkaTopics.APPLICATION_STAGE_CHANGED, groupId = GROUP)
    @Transactional
    public void onApplicationStageChanged(ApplicationStageChangedEvent event) {
        record(event.eventId(), "APPLICATION_STAGE_CHANGED", event.occurredAt(), null,
                event.studentId(), event.newStage());
    }

    private void record(String eventId, String metricKey, Instant occurredAt, BigDecimal amount,
                        Long refId, String dimension) {
        LocalDate date = occurredAt.atZone(ZoneOffset.UTC).toLocalDate();
        repository.insertIfAbsent(eventId, metricKey, occurredAt, date, amount, refId, dimension);
    }
}
