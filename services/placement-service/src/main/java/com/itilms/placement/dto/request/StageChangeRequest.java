package com.itilms.placement.dto.request;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Moving an application through the pipeline")
public record StageChangeRequest(
        @NotBlank(message = "The new stage is required")
        @Schema(description = "SHORTLISTED, INTERVIEW, ON_HOLD, SELECTED or REJECTED")
        String stage,
        @Schema(description = "Interview round, when moving to INTERVIEW. Defaults to the next round.")
        @Min(1) Integer roundNo,
        Instant nextInterviewAt,
        @Schema(description = "When SELECTED: the offer, as the company made it") @Size(max = 255) String offerDetails,
        @Size(max = 500) String note
) {
}
