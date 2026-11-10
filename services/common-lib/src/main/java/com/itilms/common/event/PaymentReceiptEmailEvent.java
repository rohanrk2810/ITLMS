package com.itilms.common.event;

import java.time.Instant;

/**
 * A payment was recorded and its receipt should reach the student by email, with the PDF attached.
 *
 * <p>The PDF travels in the event (a receipt is a few tens of kilobytes) because notification-service has no way to
 * ask finance-service for it later: a Kafka consumer holds no sign-in, and finance answers only signed-in callers.
 * {@code eventId} is the de-duplication key, so a redelivered event does not send the receipt twice.
 */
public record PaymentReceiptEmailEvent(
        String eventId,
        Instant occurredAt,
        Long userId,
        String receiptNo,
        String subject,
        String message,
        String fileName,
        byte[] pdf
) implements DomainEvent {
}
