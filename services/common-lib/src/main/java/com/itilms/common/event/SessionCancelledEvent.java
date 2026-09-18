package com.itilms.common.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A scheduled lecture was called off.
 *
 * <p>Announced rather than left implicit because other services hold things
 * that outlive the row. liveclass-service has, or is about to create, a LiveKit
 * room for this slot; without this event that room stays open and students walk
 * into a class nobody is teaching. Reporting stops counting the slot as one that
 * was supposed to happen.
 */
public record SessionCancelledEvent(
        String eventId,
        Instant occurredAt,
        Long sessionId,
        Long batchId,
        String batchCode,
        LocalDate sessionDate,
        LocalTime startTime,
        String reason
) implements DomainEvent {
}
