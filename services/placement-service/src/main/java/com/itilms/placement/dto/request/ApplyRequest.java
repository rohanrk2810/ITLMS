package com.itilms.placement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "A student's application")
public record ApplyRequest(
        @Schema(description = "Handle of a CV already uploaded to file-service") @Size(max = 120) String resumeRef,
        @Size(max = 4000) String coverNote
) {
}
