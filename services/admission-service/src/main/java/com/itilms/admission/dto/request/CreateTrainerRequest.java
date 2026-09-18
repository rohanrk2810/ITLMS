package com.itilms.admission.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create a trainer profile and its login account (Doc S6.3). */
@Schema(description = "Create a trainer")
public record CreateTrainerRequest(

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

        @Schema(example = "Java, Spring Boot, Microservices")
        @Size(max = 200)
        String specialization,

        @Size(max = 200)
        String qualification,

        @DecimalMin(value = "0.0", message = "Experience cannot be negative")
        @DecimalMax(value = "70.0", message = "Enter a realistic number of years")
        BigDecimal experienceYears,

        @Size(max = 4000)
        String bio,

        LocalDate joinedOn,

        @Schema(description = "Courses this trainer is cleared to teach. "
                + "Used to filter the trainer picker when a batch is created.")
        List<Long> courseIds
) {
}
