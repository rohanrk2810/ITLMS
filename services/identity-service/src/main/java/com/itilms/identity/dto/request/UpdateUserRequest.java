package com.itilms.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Editable account fields.
 *
 * <p>Role and status are absent by design. Changing what someone may do is a
 * different, more consequential act than fixing a typo in their surname, and it
 * gets its own endpoint, its own permission, and its own audit entry. Folding
 * both into one PUT would make a privilege escalation look like a profile edit
 * in the audit log.
 */
@Schema(description = "Update an existing user's profile fields")
public record UpdateUserRequest(

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
        String phone
) {
}
