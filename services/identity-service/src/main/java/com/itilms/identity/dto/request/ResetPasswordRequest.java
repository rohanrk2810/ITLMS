package com.itilms.identity.dto.request;

import com.itilms.identity.validation.ValidPassword;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Complete a password reset using the emailed token")
public record ResetPasswordRequest(

        @Schema(description = "The token from the reset link")
        @NotBlank(message = "Reset token is required")
        String token,

        @ValidPassword
        String newPassword
) {
}
