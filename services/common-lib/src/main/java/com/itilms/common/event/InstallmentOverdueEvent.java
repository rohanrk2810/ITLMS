package com.itilms.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * An installment passed its due date without being paid in full (Doc S16:
 * "Fee due / overdue - Student + finance").
 *
 * <p>Published once per installment, the first morning it is overdue. The
 * amount is what is still unpaid on it, not its full value: a student who paid
 * ₹8,000 of ₹10,000 is ₹2,000 overdue, and a reminder saying ₹10,000 would be
 * both wrong and the first thing they complain about.
 */
public record InstallmentOverdueEvent(
        String eventId,
        Instant occurredAt,
        Long feePlanId,
        Long installmentId,
        int installmentNo,
        Long studentId,
        Long studentUserId,
        LocalDate dueDate,
        BigDecimal amountOverdue
) implements DomainEvent {
}
