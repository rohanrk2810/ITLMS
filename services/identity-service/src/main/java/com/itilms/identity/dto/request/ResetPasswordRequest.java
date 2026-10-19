package com.itilms.identity.dto.request;

import com.itilms.identity.validation.ValidPassword;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Complete a password reset using the emailed 6-digit code")
public record ResetPasswordRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email,

        @Schema(description = "The 6-digit code from the email")
        @NotBlank(message = "Enter the 6-digit code")
        @Pattern(regexp = "\\d{6}", message = "The code is 6 digits")
        String otp,

        @ValidPassword
        String newPassword
) {
}
