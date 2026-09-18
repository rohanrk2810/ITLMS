package com.itilms.assessment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "A trainer's marks and feedback (Doc S6.10)")
public record EvaluateSubmissionRequest(

        @Schema(description = "Leave blank when returning the work for rework")
        @Min(value = 0, message = "Marks cannot be negative")
        Integer marks,

        @Size(max = 4000)
        String feedback,

        @Schema(description = "Send it back to be done again instead of marking it. "
                + "The student may then submit a new version.")
        Boolean returnForRework
) {
}
