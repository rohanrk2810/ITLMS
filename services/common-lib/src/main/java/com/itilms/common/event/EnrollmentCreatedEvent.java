package com.itilms.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A student was placed into a batch.
 *
 * <p>course-service listens so it can seed {@code lesson_progress} rows for the
 * whole curriculum; without them the student's progress bar has no denominator.
 * finance-service listens too, to raise the course fee as a fee plan the moment the student is placed, so what they
 * owe shows on their Fees page. It has no sign-in of its own to ask another service for the student's name, which is
 * why the name and code travel here.
 *
 * <p>{@code feeIncluded} marks a course the student has only because the batch holds it: finance raises no fee for it.
 * {@code feeOverride} is the price of the whole batch when the student is placed in all of it; finance then charges
 * that instead of the course fee. Both are absent (false, null) for an ordinary placement, as before.
 */
public record EnrollmentCreatedEvent(
        String eventId,
        Instant occurredAt,
        Long enrollmentId,
        Long studentId,
        Long userId,
        Long courseId,
        Long batchId,
        String batchCode,
        String studentCode,
        String studentName,
        boolean feeIncluded,
        BigDecimal feeOverride
) implements DomainEvent {

    /** An ordinary placement: the course fee applies. */
    public EnrollmentCreatedEvent(String eventId, Instant occurredAt, Long enrollmentId, Long studentId, Long userId,
                                  Long courseId, Long batchId, String batchCode, String studentCode,
                                  String studentName) {
        this(eventId, occurredAt, enrollmentId, studentId, userId, courseId, batchId, batchCode, studentCode,
                studentName, false, null);
    }

    /** Without the student's name and code (the placement is closed, or the consumer does not need them). */
    public EnrollmentCreatedEvent(String eventId, Instant occurredAt, Long enrollmentId, Long studentId, Long userId,
                                  Long courseId, Long batchId, String batchCode) {
        this(eventId, occurredAt, enrollmentId, studentId, userId, courseId, batchId, batchCode, null, null);
    }
}
