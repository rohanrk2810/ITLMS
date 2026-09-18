package com.itilms.course.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.itilms.course.entity.Course;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Course")
public record CourseResponse(
        Long id,
        String title,
        String code,
        String summary,
        String description,
        String learningOutcomes,
        String prerequisites,
        String technologyStack,
        Integer durationHours,
        String level,
        BigDecimal fee,
        String thumbnailRef,
        String status,
        Instant publishedAt,
        long moduleCount,
        long lessonCount,
        Instant createdAt
) {

    public static CourseResponse from(Course course, long moduleCount, long lessonCount) {
        return new CourseResponse(
                course.getId(), course.getTitle(), course.getCode(), course.getSummary(),
                course.getDescription(), course.getLearningOutcomes(), course.getPrerequisites(),
                course.getTechnologyStack(), course.getDurationHours(), course.getLevel().name(),
                course.getFee(), course.getThumbnailRef(), course.getStatus().name(),
                course.getPublishedAt(), moduleCount, lessonCount, course.getCreatedAt());
    }

    public static CourseResponse from(Course course) {
        return from(course, 0, 0);
    }
}
