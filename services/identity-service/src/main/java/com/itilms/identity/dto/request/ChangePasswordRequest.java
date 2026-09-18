package com.itilms.identity.dto.request;

import com.itilms.identity.validation.ValidPassword;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * A signed-in user changing their own password.
 *
 * <p>The current password is required even though the caller is already
 * authenticated: it proves the person at the keyboard is the account holder and
 * not someone who found an unlocked machine.
 */
@Schema(description = "Change your own password")
public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        String currentPassword,

        @ValidPassword
        String newPassword
) {
}
