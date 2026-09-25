package com.itilms.assessment.dto.request;

import java.time.Instant;

import com.itilms.assessment.entity.ViolationType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What a secure test's browser saw. The server decides what it counts for. */
@Schema(description = "Something the browser noticed during a secure test")
public record ViolationRequest(

        @NotNull(message = "Say what happened")
        ViolationType type,

        @Size(max = 200)
        String detail,

        @Schema(description = "When the browser says it happened. Kept for comparison, never trusted.")
        Instant clientAt
) {
}
