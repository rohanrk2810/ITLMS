package com.itilms.finance.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.common.event.KafkaTopics;
import com.itilms.finance.service.FeePlanService;

import lombok.RequiredArgsConstructor;

/**
 * Raises the course fee as a fee plan when a student is placed in a batch.
 *
 * <p>Without it a student who was approved for a class but has paid nothing sees nothing under Fees: no amount to pay
 * and no Pay online button, and the finance desk has no record of what is owed.
 */
@Component
@RequiredArgsConstructor
public class EnrollmentEventsConsumer {

    private final FeePlanService feePlanService;

    @KafkaListener(topics = KafkaTopics.ENROLLMENT_CREATED, groupId = "finance-service")
    public void onEnrolled(EnrollmentCreatedEvent event) {
        feePlanService.createFromEnrollment(event);
    }
}
