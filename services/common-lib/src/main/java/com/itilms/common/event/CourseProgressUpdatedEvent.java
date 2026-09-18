package com.itilms.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A student's completion percentage moved.
 *
 * <p>certificate-service watches for {@code allMandatoryLessonsComplete} turning
 * true; that is one of the four conditions in the completion workflow (Doc S7.3)
 * and the only one course-service can answer.
 */
public record CourseProgressUpdatedEvent(
        String eventId,
        Instant occurredAt,
        Long enrollmentId,
        Long studentId,
        Long courseId,
        Long batchId,
        BigDecimal progressPercent,
        int completedLessons,
        int totalLessons,
        boolean allMandatoryLessonsComplete
) implements DomainEvent {
}
