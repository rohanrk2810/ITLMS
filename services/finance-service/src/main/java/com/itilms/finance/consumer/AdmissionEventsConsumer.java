package com.itilms.finance.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.StudentAdmittedEvent;
import com.itilms.finance.service.FeePlanService;

import lombok.RequiredArgsConstructor;

/**
 * Raises the fee plan agreed at admission (Doc S7.1).
 *
 * <p>The admission desk enters the fee once, on the admission form. Asking the
 * finance desk to type the same figures in again is how a discount agreed at
 * admission goes missing from the fee plan.
 */
@Component
@RequiredArgsConstructor
public class AdmissionEventsConsumer {

    private final FeePlanService feePlanService;

    @KafkaListener(topics = KafkaTopics.STUDENT_ADMITTED, groupId = "finance-service")
    public void onStudentAdmitted(StudentAdmittedEvent event) {
        feePlanService.createFromAdmission(event);
    }
}
