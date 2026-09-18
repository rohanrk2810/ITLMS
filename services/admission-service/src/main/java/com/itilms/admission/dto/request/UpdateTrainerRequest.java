package com.itilms.admission.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Update a trainer profile")
public record UpdateTrainerRequest(

        @Size(max = 200)
        String specialization,

        @Size(max = 200)
        String qualification,

        @DecimalMin("0.0") @DecimalMax("70.0")
        BigDecimal experienceYears,

        @Size(max = 4000)
        String bio,

        LocalDate joinedOn,

        @Schema(allowableValues = {"ACTIVE", "INACTIVE", "ON_LEAVE"})
        @NotBlank(message = "Status is required")
        String status,

        @Schema(description = "Replaces the full list of courses this trainer may teach")
        List<Long> courseIds
) {
}
