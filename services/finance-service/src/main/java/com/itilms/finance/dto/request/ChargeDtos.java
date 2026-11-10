package com.itilms.finance.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An extra charge the finance desk adds on top of the course fee: a development fee, a book, an exam fee. */
public final class ChargeDtos {

    private ChargeDtos() {
    }

    @Schema(description = "One extra charge for one student")
    public record ChargeRequest(
            @NotBlank(message = "Name the charge, for example Development fee") @Size(max = 120) String title,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "1.00", message = "The smallest charge is 1 rupee")
            @Digits(integer = 10, fraction = 2, message = "Amount can have at most two decimal places") BigDecimal amount,
            @NotNull(message = "A due date is required") LocalDate dueDate) {
    }

    @Schema(description = "The same extra charge for every student in a batch")
    public record BatchChargeRequest(
            @NotNull(message = "Choose the batch") Long batchId,
            @NotBlank(message = "Name the charge, for example Development fee") @Size(max = 120) String title,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "1.00", message = "The smallest charge is 1 rupee")
            @Digits(integer = 10, fraction = 2, message = "Amount can have at most two decimal places") BigDecimal amount,
            @NotNull(message = "A due date is required") LocalDate dueDate) {
    }

    @Schema(description = "How many students the charge reached")
    public record BatchChargeResult(int students, int skipped) {
    }
}
