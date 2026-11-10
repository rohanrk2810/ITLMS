package com.itilms.batch.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.batch.dto.request.ValidityDtos.ExtendAccessRequest;
import com.itilms.batch.dto.request.ValidityDtos.ExtendBatchRequest;
import com.itilms.batch.dto.request.ValidityDtos.ExtendBatchResult;
import com.itilms.batch.dto.response.EnrollmentResponse;
import com.itilms.batch.entity.Batch;
import com.itilms.batch.entity.BatchStatus;
import com.itilms.batch.entity.Enrollment;
import com.itilms.batch.entity.EnrollmentStatus;
import com.itilms.batch.repository.BatchRepository;
import com.itilms.batch.repository.EnrollmentRepository;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * How long a course runs.
 *
 * <p>Courses often are not finished by the planned end date. The administrator can extend a batch (a later end date,
 * and a finished batch runs again), and can set or extend how long one student may use the course. A student's access
 * date is optional (empty means no limit, as before); once it passes, a nightly job closes the enrolment, and
 * extending it opens the enrolment again. Every change is audited with its reason, and the people concerned are told.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchValidityService {

    private static final String SERVICE_NAME = "batch-service";

    private final BatchRepository batches;
    private final EnrollmentRepository enrollments;
    private final EventPublisher events;

    // -----------------------------------------------------------------
    // A whole batch
    // -----------------------------------------------------------------

    @Transactional
    public ExtendBatchResult extendBatch(Long batchId, ExtendBatchRequest request) {
        Batch batch = batches.findById(batchId).orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));
        if (batch.getStatus() == BatchStatus.CANCELLED) {
            throw new BusinessRuleException("A cancelled batch cannot be extended.");
        }
        LocalDate previous = batch.getEndDate();
        LocalDate floor = previous != null ? previous : batch.getStartDate();
        if (!request.endDate().isAfter(floor)) {
            throw new BusinessRuleException("The new end date must be after %s.".formatted(floor));
        }
        BatchStatus before = batch.getStatus();
        batch.setEndDate(request.endDate());
        if (before == BatchStatus.COMPLETED) {
            // The nightly sweep had closed it; the course is running again.
            batch.setStatus(BatchStatus.ONGOING);
        }
        batches.save(batch);

        // Students whose own access date was the batch's old end date follow the batch.
        int extended = 0;
        if (previous != null) {
            for (Enrollment enrollment : enrollments.findByBatchId(batchId)) {
                boolean following = previous.equals(enrollment.getAccessUntil())
                        && (enrollment.getStatus() == EnrollmentStatus.ACTIVE || enrollment.getStatus() == EnrollmentStatus.EXPIRED);
                if (following) {
                    applyAccess(enrollment, request.endDate(), false);
                    extended++;
                }
            }
        }

        events.notifyBatch(batchId, "BATCH", "Your course has been extended",
                "%s now runs until %s. %s".formatted(batch.getBatchCode(), request.endDate(), request.reason().trim()),
                "/student/batches/" + batchId);
        if (batch.getTrainerUserId() != null) {
            events.notifyUsers(List.of(batch.getTrainerUserId()), "BATCH", "Batch extended",
                    "%s now runs until %s. %s".formatted(batch.getBatchCode(), request.endDate(), request.reason().trim()),
                    "/batches/" + batchId);
        }
        events.audit(SERVICE_NAME, "BATCH_EXTENDED", "Batch", batchId,
                Map.of("endDate", String.valueOf(previous), "status", before.name()),
                Map.of("endDate", request.endDate().toString(), "status", batch.getStatus().name(),
                        "reason", request.reason().trim(), "studentsExtended", extended));
        log.info("Batch {} extended from {} to {} ({} student access date(s) moved)",
                batch.getBatchCode(), previous, request.endDate(), extended);
        return new ExtendBatchResult(batchId, batch.getBatchCode(), previous, request.endDate(),
                batch.getStatus().name(), extended);
    }

    // -----------------------------------------------------------------
    // One student
    // -----------------------------------------------------------------

    @Transactional
    public EnrollmentResponse extendAccess(Long enrollmentId, ExtendAccessRequest request) {
        Enrollment enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE && enrollment.getStatus() != EnrollmentStatus.EXPIRED) {
            throw new BusinessRuleException("Only a student who is on the course (or whose access has run out) can be extended.");
        }
        if (request.accessUntil().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("The date cannot be in the past.");
        }
        LocalDate previous = enrollment.getAccessUntil();
        EnrollmentStatus before = enrollment.getStatus();
        if (before == EnrollmentStatus.EXPIRED) {
            boolean elsewhere = enrollments.findActiveForStudent(enrollment.getStudentId()).stream()
                    .anyMatch(e -> e.getCourseId().equals(enrollment.getCourseId()) && !e.getId().equals(enrollment.getId()));
            if (elsewhere) {
                throw new BusinessRuleException("This student is already on this course in another batch.");
            }
        }
        applyAccess(enrollment, request.accessUntil(), true);

        events.audit(SERVICE_NAME, "ACCESS_EXTENDED", "Enrollment", enrollmentId,
                Map.of("accessUntil", String.valueOf(previous), "status", before.name()),
                Map.of("accessUntil", request.accessUntil().toString(), "reason", request.reason().trim()));
        return EnrollmentResponse.from(enrollment);
    }

    /** Sets the access date, opens an expired enrolment again, and (when asked) tells the student. */
    private void applyAccess(Enrollment enrollment, LocalDate until, boolean tellStudent) {
        enrollment.setAccessUntil(until);
        boolean reopen = enrollment.getStatus() == EnrollmentStatus.EXPIRED && !until.isBefore(LocalDate.now());
        if (reopen) {
            enrollment.setStatus(EnrollmentStatus.ACTIVE);
            // Everything that listens (the course mirror, notifications, reports) learns the student is on the course.
            events.publishAfterCommit(KafkaTopics.ENROLLMENT_CREATED, new EnrollmentCreatedEvent(
                    DomainEvent.newId(), Instant.now(), enrollment.getId(), enrollment.getStudentId(),
                    enrollment.getUserId(), enrollment.getCourseId(), enrollment.getBatchId(), null,
                    enrollment.getStudentCode(), enrollment.getStudentName()));
        }
        enrollments.save(enrollment);
        if (tellStudent && enrollment.getUserId() != null) {
            events.notifyUsers(List.of(enrollment.getUserId()), "ENROLLMENT", "Your course access has been extended",
                    "You can use the course until %s.".formatted(until), "/student/courses");
        }
    }

    /** The active students and those whose access ran out, by name: what the batch page needs to offer an extension. */
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> rosterWithExpired(Long batchId) {
        List<Enrollment> all = new java.util.ArrayList<>(enrollments.findByBatchIdAndStatus(batchId, EnrollmentStatus.ACTIVE));
        all.addAll(enrollments.findByBatchIdAndStatus(batchId, EnrollmentStatus.EXPIRED));
        all.sort(java.util.Comparator.comparing(e -> e.getStudentName() == null ? "" : e.getStudentName()));
        return all.stream().map(EnrollmentResponse::from).toList();
    }

    // -----------------------------------------------------------------
    // The nightly sweep
    // -----------------------------------------------------------------

    /** Closes the enrolments whose access date has passed. Returns how many were closed. */
    @Transactional
    public int expireOverdue(LocalDate today) {
        List<Enrollment> overdue = enrollments.findByStatusAndAccessUntilBefore(EnrollmentStatus.ACTIVE, today);
        for (Enrollment enrollment : overdue) {
            enrollment.setStatus(EnrollmentStatus.EXPIRED);
            events.publishAfterCommit(KafkaTopics.ENROLLMENT_CLOSED, new EnrollmentCreatedEvent(
                    DomainEvent.newId(), Instant.now(), enrollment.getId(), enrollment.getStudentId(),
                    enrollment.getUserId(), enrollment.getCourseId(), enrollment.getBatchId(), null));
            if (enrollment.getUserId() != null) {
                events.notifyUsers(List.of(enrollment.getUserId()), "ENROLLMENT", "Your course access has ended",
                        "Your access ended on %s. Ask the institute to extend it if you still need the course."
                                .formatted(enrollment.getAccessUntil()), "/student/courses");
            }
        }
        enrollments.saveAll(overdue);
        if (!overdue.isEmpty()) {
            events.audit(SERVICE_NAME, "ACCESS_EXPIRED", "Enrollment", null, null, Map.of("students", overdue.size()));
            log.info("{} student access date(s) passed; enrolments closed", overdue.size());
        }
        return overdue.size();
    }
}
