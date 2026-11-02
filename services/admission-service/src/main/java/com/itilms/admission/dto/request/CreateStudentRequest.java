package com.itilms.admission.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admit a student directly, without a lead (Doc S6.2).
 *
 * <p>Creates the login account in identity-service as well as the profile here,
 * so a coordinator performs one action rather than two that can get out of step.
 */
@Schema(description = "Create a student profile and its login account")
public record CreateStudentRequest(

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

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9+\\- ]{8,20}$", message = "Enter a valid phone number")
        String phone,

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
        String emergencyContact,

        @Schema(description = "Defaults to today")
        LocalDate admissionDate,

        @Size(max = 4000)
        String remarks
) {
}
