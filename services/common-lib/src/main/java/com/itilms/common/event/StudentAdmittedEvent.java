package com.itilms.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A student was admitted (Doc S7.1, Admission Workflow).
 *
 * <p>This single event drives the rest of the admission chain: finance-service
 * creates the fee plan, batch-service enrols the student if a batch was chosen,
 * and notification-service sends the welcome message. Publishing one complete
 * fact rather than three separate commands means a consumer that was offline
 * can catch up from the log and reach the same end state.
 *
 * <p>The course, batch and fee fields are nullable because there are two ways in.
 * Converting a lead settles all of it at once, so they are populated. Creating a
 * student profile directly — a back-office correction, a data import — records
 * the person before any commercial terms exist, and those fields stay empty
 * until a batch and a fee plan are assigned separately. Consumers must treat a
 * null {@code totalFee} as "no plan to create yet", not as "a plan for zero".
 */
public record StudentAdmittedEvent(
        String eventId,
        Instant occurredAt,
        Long studentId,
        Long userId,
        String studentCode,
        String fullName,
        String email,
        String phone,
        Long courseId,
        Long batchId,
        LocalDate admissionDate,
        Long admittedByUserId,

        /** Agreed fee before discount. Null when no commercial terms were set. */
        BigDecimal totalFee,
        BigDecimal discount,
        /** How many installments to split the net fee into. Null when no plan applies. */
        Integer installments
) implements DomainEvent {

    /** Profile created with no course, batch or fee agreed yet. */
    public static StudentAdmittedEvent profileOnly(Long studentId, Long userId, String studentCode,
                                                   String fullName, String email, String phone,
                                                   LocalDate admissionDate, Long admittedByUserId) {
        return new StudentAdmittedEvent(DomainEvent.newId(), Instant.now(),
                studentId, userId, studentCode, fullName, email, phone,
                null, null, admissionDate, admittedByUserId,
                null, null, null);
    }

    /** True when there is enough here for finance-service to raise a fee plan. */
    public boolean hasFeePlan() {
        return totalFee != null && courseId != null;
    }
}
