package com.itilms.admission.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The details taken when a student registers, before the login account exists.
 *
 * <p>Everything the institute needs on file is required here; only the college,
 * graduation year and emergency contact may be left out.
 */
@Schema(description = "Personal, education, address and guardian details of a student being admitted")
public record StudentProfileRequest(

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @NotBlank(message = "Gender is required")
        @Schema(allowableValues = {"MALE", "FEMALE", "OTHER"})
        String gender,

        @NotBlank(message = "Highest education is required")
        @Size(max = 160)
        String highestEducation,

        @Size(max = 160)
        String college,

        @Min(value = 1950, message = "Enter a valid graduation year")
        @Max(value = 2100, message = "Enter a valid graduation year")
        Integer graduationYear,

        @NotBlank(message = "Address is required")
        @Size(max = 255)
        String addressLine,

        @NotBlank(message = "City is required")
        @Size(max = 80)
        String city,

        @NotBlank(message = "State is required")
        @Size(max = 80)
        String state,

        @NotBlank(message = "PIN code is required")
        @Pattern(regexp = "^[0-9]{4,10}$", message = "Enter a valid PIN code")
        String pincode,

        @NotBlank(message = "Guardian name is required")
        @Size(max = 120)
        String guardianName,

        @NotBlank(message = "Guardian phone number is required")
        @Pattern(regexp = "^[0-9+\\- ]{8,20}$", message = "Enter a valid guardian phone number")
        String guardianPhone,

        @Pattern(regexp = "^$|^[0-9+\\- ]{8,20}$", message = "Enter a valid emergency contact")
        String emergencyContact
) {
}
