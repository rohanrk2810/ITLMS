package com.itilms.course.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create a course (Doc S6.5).
 *
 * <p>Only the title and code are mandatory here. Courses are written over
 * several sittings, and refusing to save a draft until every field is filled in
 * makes people compose in a text editor and paste — which is how half-finished
 * descriptions end up published. The completeness rules apply at publish time
 * instead, where they matter.
 */
@Schema(description = "Create a course in DRAFT status")
public record CreateCourseRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 160)
        String title,

        @Schema(example = "JFS", description = "Short code; also used to build batch codes")
        @NotBlank(message = "Course code is required")
        @Size(max = 30)
        @Pattern(regexp = "^[A-Za-z0-9-]{2,30}$",
                message = "Course code may contain letters, digits and hyphens only")
        String code,

        @Size(max = 500)
        String summary,

        String description,

        String learningOutcomes,

        String prerequisites,

        @Schema(example = "Java, Spring Boot, React, PostgreSQL")
        @Size(max = 400)
        String technologyStack,

        @Min(value = 0, message = "Duration cannot be negative")
        Integer durationHours,

        @Schema(allowableValues = {"BEGINNER", "INTERMEDIATE", "ADVANCED"})
        String level,

        @Schema(description = "List price. What a student actually pays is agreed at admission.")
        @DecimalMin(value = "0.0", message = "Fee cannot be negative")
        BigDecimal fee,

        @Schema(description = "file-service handle for the catalog image")
        String thumbnailRef
) {
}
