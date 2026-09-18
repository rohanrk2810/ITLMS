package com.itilms.course.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create or update a module.
 *
 * <p>{@code sequenceNo} may be omitted on create, in which case the module is
 * appended to the end — which is what someone building a curriculum top to
 * bottom actually wants, and spares them counting existing modules.
 */
@Schema(description = "Course module")
public record ModuleRequest(

        @NotBlank(message = "Module title is required")
        @Size(max = 160)
        String title,

        String description,

        @Schema(description = "Position within the course. Appended to the end when omitted.")
        @Min(value = 1, message = "Sequence starts at 1")
        Integer sequenceNo
) {
}
