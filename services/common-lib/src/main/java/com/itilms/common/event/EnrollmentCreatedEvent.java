package com.itilms.common.event;

import java.time.Instant;

/**
 * A student was placed into a batch.
 *
 * <p>course-service listens so it can seed {@code lesson_progress} rows for the
 * whole curriculum; without them the student's progress bar has no denominator.
 */
public record EnrollmentCreatedEvent(
        String eventId,
        Instant occurredAt,
        Long enrollmentId,
        Long studentId,
        Long userId,
        Long courseId,
        Long batchId,
        String batchCode
) implements DomainEvent {
}
