package com.itilms.common.event;

import java.time.Instant;

/** New learning material is available to a course's batches (Doc S16). */
public record LessonPublishedEvent(
        String eventId,
        Instant occurredAt,
        Long lessonId,
        Long moduleId,
        Long courseId,
        String courseTitle,
        String lessonTitle,
        String lessonType
) implements DomainEvent {
}
