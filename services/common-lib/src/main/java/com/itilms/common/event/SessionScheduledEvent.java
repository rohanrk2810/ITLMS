package com.itilms.common.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A lecture slot was created or moved.
 *
 * <p>liveclass-service consumes this to pre-provision a LiveKit room for online
 * and hybrid sessions, so the trainer never waits for room creation at class
 * time. notification-service tells the affected students (Doc S16).
 */
public record SessionScheduledEvent(
        String eventId,
        Instant occurredAt,
        Long sessionId,
        Long batchId,
        String batchCode,
        Long trainerId,
        Long trainerUserId,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        String topic,
        String mode,
        boolean rescheduled
) implements DomainEvent {
}
