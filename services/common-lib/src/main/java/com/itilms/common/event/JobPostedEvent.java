package com.itilms.common.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A placement opening went live.
 *
 * <p>{@code eligibleCourseIds} and the score thresholds travel with the event so
 * notification-service can fan out to "eligible students" (Doc S16) without
 * calling back into placement-service for the rules.
 */
public record JobPostedEvent(
        String eventId,
        Instant occurredAt,
        Long jobId,
        Long companyId,
        String companyName,
        String title,
        String packageOffered,
        LocalDate applicationDeadline,
        List<Long> eligibleCourseIds,
        Integer minAttendancePercent,
        Integer minScorePercent
) implements DomainEvent {
}
