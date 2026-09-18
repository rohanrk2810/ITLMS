package com.itilms.identity.dto.request;

import com.itilms.identity.validation.ValidPassword;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public self-registration (Doc S8.1, S11).
 *
 * <p>Deliberately narrow: a visitor can create a STUDENT account and nothing
 * else. The role is not a field, because a request body is under the caller's
 * control and a {@code role} field here would be an open invitation to register
 * as ADMIN. Staff accounts are created by staff, through {@code POST /api/users}.
 */
@Schema(description = "Public student self-registration")
public record RegisterRequest(

        @Schema(example = "Priya")
        @NotBlank(message = "First name is required")
        @Size(max = 80, message = "First name must be at most 80 characters")
        String firstName,

        @Schema(example = "Sharma")
        @NotBlank(message = "Last name is required")
        @Size(max = 80, message = "Last name must be at most 80 characters")
        String lastName,

        @Schema(example = "priya.sharma@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 160, message = "Email must be at most 160 characters")
        String email,

        @Schema(example = "9876543210")
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9+\\- ]{8,20}$", message = "Enter a valid phone number")
        String phone,

        @Schema(example = "Secret@123")
        @ValidPassword
        String password
) {
}
