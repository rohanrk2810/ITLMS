package com.itilms.assessment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Give one student more attempts on a test")
public record ExtraAttemptRequest(
        @Schema(description = "How many more attempts to give. Default 1.")
        @Min(value = 1, message = "Give at least 1 attempt")
        @Max(value = 5, message = "Give at most 5 attempts at a time")
        Integer count
) {
}
