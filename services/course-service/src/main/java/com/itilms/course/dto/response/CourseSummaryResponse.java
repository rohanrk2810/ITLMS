package com.itilms.course.dto.response;

import java.math.BigDecimal;

import com.itilms.course.entity.Course;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A catalog card.
 *
 * <p>This is also what the <em>public</em> catalog returns, so it must contain
 * nothing an institute would not put on its website. The full description and
 * the curriculum are behind the detail endpoint, which applies enrolment rules.
 */
@Schema(description = "Course as it appears in a catalog listing")
public record CourseSummaryResponse(
        Long id,
        String title,
        String code,
        String summary,
        String technologyStack,
        Integer durationHours,
        String level,
        BigDecimal fee,
        String thumbnailRef,
        String status
) {

    public static CourseSummaryResponse from(Course course) {
        return new CourseSummaryResponse(
                course.getId(), course.getTitle(), course.getCode(), course.getSummary(),
                course.getTechnologyStack(), course.getDurationHours(), course.getLevel().name(),
                course.getFee(), course.getThumbnailRef(), course.getStatus().name());
    }
}
