package com.itilms.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Activate, deactivate or block an account.
 *
 * <p>The reason is mandatory. Doc S12 wants privileged actions audited, and an
 * audit row reading "admin blocked user 214" six months later is almost useless
 * without it.
 */
@Schema(description = "Change an account's status")
public record UpdateStatusRequest(

        @Schema(example = "BLOCKED", allowableValues = {"ACTIVE", "INACTIVE", "BLOCKED"})
        @NotBlank(message = "Status is required")
        String status,

        @Schema(example = "Course completed and fees cleared")
        @NotBlank(message = "A reason is required for audit purposes")
        @Size(max = 255)
        String reason
) {
}
