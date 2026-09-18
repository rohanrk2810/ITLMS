package com.itilms.common.event;

import java.time.Instant;
import java.util.List;

/**
 * A live class ended and liveclass-service worked out who actually attended.
 *
 * <p>batch-service consumes this and writes the {@code attendance} rows, so an
 * online session is marked without the trainer ticking 40 checkboxes. The
 * trainer can still override afterwards; that override is an audited correction
 * like any other (Doc S6.9).
 *
 * <p>The PRESENT/LATE/ABSENT decision uses the thresholds configured per
 * institute, not a hard-coded rule, so a 3-hour batch and a 1-hour batch can
 * both be judged fairly.
 */
public record LiveAttendanceComputedEvent(
        String eventId,
        Instant occurredAt,
        Long liveSessionId,
        Long classSessionId,
        Long batchId,
        int sessionDurationSeconds,
        List<Entry> entries
) implements DomainEvent {

    /**
     * @param attendedSeconds total time in the room across all reconnects
     * @param status          PRESENT, LATE or ABSENT as computed from thresholds
     */
    public record Entry(Long studentId, Long userId, int attendedSeconds,
                        int attendancePercent, String status) {
    }
}
