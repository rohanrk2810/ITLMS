package com.itilms.course.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.common.event.KafkaTopics;
import com.itilms.course.service.ProgressService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the local enrolment mirror in step with batch-service.
 *
 * <p>Without this, a student enrolled into a batch would open their course and
 * find no progress record to write against — the lesson player would work and
 * nothing it reported would be saved.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentEventConsumer {

    private final ProgressService progressService;

    @KafkaListener(topics = KafkaTopics.ENROLLMENT_CREATED, groupId = "course-service")
    public void onEnrollmentCreated(EnrollmentCreatedEvent event) {
        log.debug("Mirroring enrolment {} for student {}", event.enrollmentId(), event.studentId());
        progressService.mirrorEnrollment(event);
    }

    /**
     * A student left the batch.
     *
     * <p>Their progress rows are kept. If they return, or if a dispute arises
     * about what they completed, deleting the record would destroy the only
     * evidence — and it costs almost nothing to retain.
     */
    @KafkaListener(topics = KafkaTopics.ENROLLMENT_CLOSED, groupId = "course-service")
    public void onEnrollmentClosed(EnrollmentCreatedEvent event) {
        progressService.closeEnrollment(event.enrollmentId(), "DROPPED");
    }
}
