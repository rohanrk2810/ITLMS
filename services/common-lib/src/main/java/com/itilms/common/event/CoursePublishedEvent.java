package com.itilms.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A course became visible to students.
 *
 * <p>Distinct from {@link LessonPublishedEvent}: that announces one new piece of
 * material inside a course people are already taking, while this announces that
 * the course itself is now on offer. The audiences differ — the first goes to a
 * batch, the second is catalog news — so they are separate topics rather than
 * one event with a mode flag.
 */
public record CoursePublishedEvent(
        String eventId,
        Instant occurredAt,
        Long courseId,
        String courseCode,
        String title,
        String level,
        BigDecimal fee,
        int moduleCount,
        int lessonCount,
        Long publishedByUserId
) implements DomainEvent {
}
