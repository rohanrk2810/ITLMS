package com.itilms.admission.dto.request;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Record a contact attempt and schedule the next one. */
@Schema(description = "Log a follow-up against a lead")
public record AddFollowupRequest(

        @Schema(example = "CALLED",
                allowableValues = {"CALLED", "NO_ANSWER", "VISITED", "EMAILED", "MESSAGED", "MEETING", "OTHER"})
        @NotBlank(message = "Outcome is required")
        String outcome,

        @Size(max = 4000)
        String remark,

        @Schema(description = "When to try again. Clears the lead's follow-up date when omitted.")
        Instant nextActionAt,

        @Schema(description = "Optional status transition to apply alongside this follow-up",
                allowableValues = {"CONTACTED", "FOLLOW_UP", "INTERESTED", "NOT_INTERESTED", "LOST"})
        String newStatus
) {
}
