package com.itilms.admission.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edit a student's own details.
 *
 * <p>Name, email and phone are absent: those live on the account in
 * identity-service, and having two editable copies is how a system ends up
 * addressing someone by a name they changed a year ago. They are updated
 * through {@code PUT /api/users/{id}} and arrive here as an event.
 */
@Schema(description = "Update a student profile")
public record UpdateStudentRequest(

        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @Schema(allowableValues = {"MALE", "FEMALE", "OTHER"})
        String gender,

        @Size(max = 160)
        String highestEducation,

        @Size(max = 160)
        String college,

        @Min(1950) @Max(2100)
        Integer graduationYear,

        @Size(max = 255)
        String addressLine,

        @Size(max = 80)
        String city,

        @Size(max = 80)
        String state,

        @Pattern(regexp = "^$|^[0-9]{4,10}$", message = "Enter a valid PIN code")
        String pincode,

        @Size(max = 120)
        String guardianName,

        @Pattern(regexp = "^$|^[0-9+\\- ]{8,20}$", message = "Enter a valid guardian phone number")
        String guardianPhone,

        @Pattern(regexp = "^$|^[0-9+\\- ]{8,20}$", message = "Enter a valid emergency contact")
        String emergencyContact,

        @Size(max = 4000)
        String remarks
) {
}
