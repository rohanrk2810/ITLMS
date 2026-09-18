package com.itilms.admission.dto.request;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edit a lead.
 *
 * <p>{@code status} is editable here but CONVERTED is rejected by the service:
 * conversion creates a student account and a fee plan, so it has to go through
 * {@code POST /api/leads/{id}/convert} rather than being achievable by setting
 * a field.
 */
@Schema(description = "Update a lead")
public record UpdateLeadRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 120)
        String name,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9+\\- ]{8,20}$", message = "Enter a valid phone number")
        String phone,

        @Email(message = "Enter a valid email address")
        @Size(max = 160)
        String email,

        @NotBlank(message = "Source is required")
        String source,

        @Schema(allowableValues = {"NEW", "CONTACTED", "FOLLOW_UP", "INTERESTED", "NOT_INTERESTED", "LOST"})
        @NotBlank(message = "Status is required")
        String status,

        Long interestedCourseId,

        Long counselorUserId,

        Instant nextFollowUpAt,

        @Size(max = 4000)
        String notes,

        @Schema(description = "Required when status is LOST or NOT_INTERESTED")
        @Size(max = 255)
        String lostReason
) {
}
