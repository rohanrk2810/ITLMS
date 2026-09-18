package com.itilms.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A student now owes a fee (Doc S7.1: "Fee Plan Created").
 *
 * <p>notification-service tells the student the amount and the first due date;
 * reporting-service adds it to the total billed.
 */
public record FeePlanCreatedEvent(
        String eventId,
        Instant occurredAt,
        Long feePlanId,
        Long studentId,
        Long studentUserId,
        Long courseId,
        BigDecimal netFee,
        int installments,
        LocalDate firstDueDate
) implements DomainEvent {
}
