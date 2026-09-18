package com.itilms.course.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.itilms.course.entity.CourseEnrollment;

import io.swagger.v3.oas.annotations.media.Schema;

/** How far a student has got (Doc S15, student dashboard). */
@Schema(description = "Progress through a course")
public record ProgressResponse(
        Long enrollmentId,
        Long studentId,
        Long courseId,
        Long batchId,
        String status,
        BigDecimal progressPercent,
        int completedLessons,
        int totalLessons,
        @Schema(description = "True when every mandatory lesson is done - "
                + "one of the four certificate conditions")
        boolean allLessonsComplete,
        Instant completedAt
) {

    public static ProgressResponse from(CourseEnrollment enrollment) {
        return new ProgressResponse(
                enrollment.getEnrollmentId(),
                enrollment.getStudentId(),
                enrollment.getCourseId(),
                enrollment.getBatchId(),
                enrollment.getStatus().name(),
                enrollment.getProgressPercent(),
                enrollment.getCompletedLessons(),
                enrollment.getTotalLessons(),
                enrollment.allLessonsComplete(),
                enrollment.getCompletedAt());
    }
}
