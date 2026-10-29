package com.itilms.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WhatsAppVerifyRequest(
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9+\\- ]{8,20}$", message = "Enter a valid phone number")
        String phone,
        @NotBlank(message = "Enter the 6-digit code")
        @Pattern(regexp = "^[0-9]{6}$", message = "The code is 6 digits")
        String code
) {
}
