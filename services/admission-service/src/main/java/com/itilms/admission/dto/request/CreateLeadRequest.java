package com.itilms.admission.dto.request;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A lead created by staff — a walk-in, a phone call, a referral (Doc S6.4). */
@Schema(description = "Create a lead")
public record CreateLeadRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 120)
        String name,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9+\\- ]{8,20}$", message = "Enter a valid phone number")
        String phone,

        @Email(message = "Enter a valid email address")
        @Size(max = 160)
        String email,

        @Schema(example = "WALK_IN",
                allowableValues = {"WALK_IN", "WEBSITE", "REFERRAL", "PHONE", "SOCIAL_MEDIA", "CAMPAIGN", "OTHER"})
        @NotBlank(message = "Source is required")
        String source,

        Long interestedCourseId,

        @Schema(description = "Staff member responsible. Defaults to the creator when omitted.")
        Long counselorUserId,

        @Schema(description = "When to call back")
        Instant nextFollowUpAt,

        @Size(max = 4000)
        String notes
) {
}
