package com.itilms.common.event;

import java.time.Instant;

/**
 * An application moved through the interview pipeline.
 *
 * <p>Doc S14 requires these transitions to be auditable, so every change is
 * emitted here and the audit consumer persists the before/after pair.
 */
public record ApplicationStageChangedEvent(
        String eventId,
        Instant occurredAt,
        Long applicationId,
        Long jobId,
        String jobTitle,
        String companyName,
        Long studentId,
        Long studentUserId,
        String previousStage,
        String newStage,
        String finalStatus,
        Long changedByUserId
) implements DomainEvent {
}
