package com.itilms.admission.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The contact form on the public website (Doc S8.1).
 *
 * <p>Anonymous, so it is kept deliberately thin: a name, a way to call back,
 * and what they are interested in. Every extra field on a public form is
 * something a bot can fill and a human will abandon.
 *
 * <p>Note what is <em>absent</em>: counselor, status, and follow-up date. Those
 * are the institute's own workflow fields, and letting an anonymous caller set
 * them would mean a stranger could assign work to staff.
 */
@Schema(description = "Public course enquiry")
public record PublicEnquiryRequest(

        @NotBlank(message = "Your name is required")
        @Size(max = 120)
        String name,

        @NotBlank(message = "A phone number is required so we can call you back")
        @Pattern(regexp = "^[0-9+\\- ]{8,20}$", message = "Enter a valid phone number")
        String phone,

        @Email(message = "Enter a valid email address")
        @Size(max = 160)
        String email,

        @Schema(description = "The course they asked about, if any")
        Long interestedCourseId,

        @Size(max = 1000, message = "Please keep your message under 1000 characters")
        String message
) {
}
