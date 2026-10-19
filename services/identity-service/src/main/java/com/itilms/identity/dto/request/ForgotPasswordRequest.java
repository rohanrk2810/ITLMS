package com.itilms.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request a password reset link")
public record ForgotPasswordRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email,
        @Schema(description = "The 'verify you are human' token; required only when the captcha is switched on")
        @Size(max = 4096, message = "Captcha token is too long")
        String captchaToken
) {

    public ForgotPasswordRequest(String email) {
        this(email, null);
    }
}
