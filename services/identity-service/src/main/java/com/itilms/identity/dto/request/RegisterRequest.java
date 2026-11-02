package com.itilms.identity.dto.request;

import java.time.LocalDate;

import com.itilms.common.event.StudentDetails;
import com.itilms.identity.validation.ValidPassword;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
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
        String password,
        @Schema(description = "The 'verify you are human' token; required only when the captcha is switched on")
        @Size(max = 4096, message = "Captcha token is too long")
        String captchaToken,

        @Schema(description = "Everything the institute keeps on file; the account is created only with it")
        @NotNull(message = "Your details are required")
        @Valid
        Details details
) {

    public RegisterRequest(String firstName, String lastName, String email, String phone, String password) {
        this(firstName, lastName, email, phone, password, null, null);
    }

    /** The student's personal, education, address and guardian details. */
    public record Details(
            @NotNull(message = "Date of birth is required")
            @Past(message = "Date of birth must be in the past")
            LocalDate dateOfBirth,

            @NotBlank(message = "Gender is required")
            @Pattern(regexp = "(?i)MALE|FEMALE|OTHER", message = "Gender must be MALE, FEMALE or OTHER")
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
        public StudentDetails toEvent() {
            return new StudentDetails(dateOfBirth, gender == null ? null : gender.trim().toUpperCase(),
                    highestEducation, college, graduationYear, addressLine, city, state, pincode,
                    guardianName, guardianPhone, emergencyContact);
        }
    }
}
