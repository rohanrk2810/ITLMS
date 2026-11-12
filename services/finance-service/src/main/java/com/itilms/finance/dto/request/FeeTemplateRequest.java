package com.itilms.finance.dto.request;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "The usual fee structure of a course")
public record FeeTemplateRequest(
        @NotNull @Min(1) @Max(36) Integer installmentCount,
        @NotNull @Min(0) @Max(365) Integer firstDueDays,
        @Valid @Size(max = 20) List<Charge> charges) {

    public record Charge(
            @NotBlank(message = "Name the charge") @Size(max = 120) String label,
            @NotNull @DecimalMin(value = "1.00", message = "The smallest charge is 1 rupee") BigDecimal amount,
            @NotNull @Min(0) @Max(365) Integer dueDays) {
    }
}
