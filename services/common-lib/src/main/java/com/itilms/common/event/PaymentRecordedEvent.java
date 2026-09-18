package com.itilms.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Finance recorded a payment against an installment.
 *
 * <p>{@code outstandingAfter} is included so consumers do not have to re-derive
 * "Net Fee - sum(successful payments)" (Doc S14); finance-service owns that sum
 * and states the result here.
 */
public record PaymentRecordedEvent(
        String eventId,
        Instant occurredAt,
        Long paymentId,
        Long feePlanId,
        Long installmentId,
        Long studentId,
        Long studentUserId,
        BigDecimal amount,
        BigDecimal outstandingAfter,
        String method,
        String receiptNo,
        Long recordedByUserId
) implements DomainEvent {
}
