package com.itilms.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A trainer graded a submission. certificate-service tracks these to decide
 * whether "required assignments complete" is satisfied (Doc S7.3).
 */
public record SubmissionEvaluatedEvent(
        String eventId,
        Instant occurredAt,
        Long submissionId,
        Long assignmentId,
        Long batchId,
        Long studentId,
        Long studentUserId,
        BigDecimal marks,
        Integer maxMarks,
        String status,
        Long evaluatedByUserId
) implements DomainEvent {
}
