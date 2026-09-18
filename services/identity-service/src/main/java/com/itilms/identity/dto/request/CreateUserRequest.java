package com.itilms.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Staff creating an account for someone else (Doc S6.2, S6.3).
 *
 * <p>When {@code password} is omitted the service generates a temporary one and
 * flags the account so the holder must change it at first sign-in. That is the
 * normal path: a coordinator adding thirty students should not be inventing and
 * transcribing thirty passwords, and the ones they would invent would all look
 * alike.
 */
@Schema(description = "Create a user account (staff only)")
public record CreateUserRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 80)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 80)
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 160)
        String email,

        @Pattern(regexp = "^$|^[0-9+\\- ]{8,20}$", message = "Enter a valid phone number")
        String phone,

        @Schema(example = "TRAINER",
                allowableValues = {"ADMIN", "COORDINATOR", "TRAINER", "STUDENT", "PLACEMENT", "FINANCE"})
        @NotBlank(message = "Role is required")
        String role,

        @Schema(description = "Leave blank to generate a temporary password the user must change")
        String password
) {
}
