package com.itilms.common.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Attendance was saved (or corrected) for one session.
 *
 * <p>Carries the per-student rows rather than just a count, because
 * reporting-service maintains a pre-aggregated attendance percentage per
 * student and cannot recompute it without knowing who was marked what.
 *
 * @param correction true when this replaced an earlier marking. Doc S6.9 and
 *                   S14 require corrections to be audited, so this flag is what
 *                   the audit consumer keys on.
 */
public record AttendanceMarkedEvent(
        String eventId,
        Instant occurredAt,
        Long sessionId,
        Long batchId,
        LocalDate sessionDate,
        Long markedByUserId,
        boolean correction,
        List<Entry> entries
) implements DomainEvent {

    public record Entry(Long studentId, String status) {
    }
}
