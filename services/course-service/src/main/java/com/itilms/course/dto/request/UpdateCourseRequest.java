package com.itilms.course.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edit a course.
 *
 * <p>{@code status} is absent: publishing and archiving have their own
 * endpoints because each runs checks and emits an event, neither of which
 * belongs in a general-purpose PUT.
 */
@Schema(description = "Update a course")
public record UpdateCourseRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 160)
        String title,

        @NotBlank(message = "Course code is required")
        @Pattern(regexp = "^[A-Za-z0-9-]{2,30}$",
                message = "Course code may contain letters, digits and hyphens only")
        String code,

        @Size(max = 500)
        String summary,

        String description,

        String learningOutcomes,

        String prerequisites,

        @Size(max = 400)
        String technologyStack,

        @Min(0)
        Integer durationHours,

        @Schema(allowableValues = {"BEGINNER", "INTERMEDIATE", "ADVANCED"})
        String level,

        @DecimalMin("0.0")
        BigDecimal fee,

        String thumbnailRef
) {
}
