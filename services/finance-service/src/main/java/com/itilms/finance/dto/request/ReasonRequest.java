package com.itilms.finance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why a payment is reversed or a plan cancelled. Always required: both are audited. */
@Schema(description = "The reason for a reversal or cancellation")
public record ReasonRequest(
        @NotBlank(message = "A reason is required")
        @Size(max = 255)
        String reason
) {
}
