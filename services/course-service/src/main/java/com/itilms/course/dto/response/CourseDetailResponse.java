package com.itilms.course.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A course with its full curriculum, shaped for the caller.
 *
 * <p>The same endpoint serves a visitor browsing the catalog and a student
 * working through the material. Rather than two near-identical endpoints that
 * drift apart, one response carries {@code enrolled} and lets the lessons
 * themselves say whether their content came through.
 */
@Schema(description = "Course with curriculum")
public record CourseDetailResponse(
        CourseResponse course,
        List<ModuleResponse> modules,
        @Schema(description = "True when the caller has an active enrolment on this course")
        boolean enrolled,
        @Schema(description = "The caller's progress, when enrolled")
        ProgressResponse progress
) {
}
