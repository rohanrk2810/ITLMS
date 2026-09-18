package com.itilms.common.event;

import java.time.Instant;

/** A trainer published an assignment to a batch. */
public record AssignmentCreatedEvent(
        String eventId,
        Instant occurredAt,
        Long assignmentId,
        Long batchId,
        String title,
        Instant dueAt,
        Integer maxMarks,
        Long createdByUserId
) implements DomainEvent {
}
