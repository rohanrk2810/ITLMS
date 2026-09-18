package com.itilms.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Doc S6.1: sign in with email or phone plus password.
 *
 * <p>Note there is no password policy check here. The policy applies when a
 * password is <em>set</em>; applying it at login would reject users whose
 * existing password predates a tightened rule, and would leak the policy to
 * anyone probing the endpoint.
 */
@Schema(description = "Credentials for sign-in")
public record LoginRequest(

        @Schema(example = "priya.sharma@example.com", description = "Registered email address or phone number")
        @NotBlank(message = "Email or phone is required")
        @Size(max = 160, message = "Identifier is too long")
        String identifier,

        @Schema(example = "Secret@123")
        @NotBlank(message = "Password is required")
        @Size(max = 200, message = "Password is too long")
        String password
) {
}
