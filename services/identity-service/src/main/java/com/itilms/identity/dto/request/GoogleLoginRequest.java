package com.itilms.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(
        @NotBlank(message = "The Google sign-in token is required")
        @Size(max = 8192, message = "The Google sign-in token is too long")
        String idToken
) {
}
