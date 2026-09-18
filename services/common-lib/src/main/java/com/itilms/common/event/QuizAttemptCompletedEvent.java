package com.itilms.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A student submitted a test and the server scored it.
 *
 * <p>Doc S14 is explicit that the score is computed server-side and never
 * trusted from the browser, so this event is the authoritative result record
 * for reporting and certification.
 */
public record QuizAttemptCompletedEvent(
        String eventId,
        Instant occurredAt,
        Long attemptId,
        Long quizId,
        Long courseId,
        Long studentId,
        Long studentUserId,
        String quizTitle,
        BigDecimal score,
        BigDecimal percentage,
        boolean passed,
        int attemptNo
) implements DomainEvent {
}
