package com.itilms.common.event;

import java.time.Instant;
import java.time.LocalDate;

/** A certificate was generated and is publicly verifiable (Doc S6.13). */
public record CertificateIssuedEvent(
        String eventId,
        Instant occurredAt,
        Long certificateId,
        String certificateNo,
        Long studentId,
        Long studentUserId,
        Long courseId,
        String courseTitle,
        LocalDate issueDate,
        String verificationUrl
) implements DomainEvent {
}
