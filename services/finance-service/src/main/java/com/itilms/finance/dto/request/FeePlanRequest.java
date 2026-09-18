package com.itilms.finance.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A fee plan's terms (Doc S6.12: "total fee, discounts, installment schedule
 * and due dates").
 *
 * <p>The schedule is given one of two ways: a count and a first due date, to
 * split evenly at monthly intervals; or an explicit list, for an institute that
 * agrees uneven amounts. An explicit list must add up to the net fee exactly.
 */
@Schema(description = "A fee plan for one student and one course")
public record FeePlanRequest(

        @Schema(description = "Required when creating; ignored when updating")
        Long studentId,

        @Schema(description = "Required when creating; ignored when updating")
        Long courseId,

        Long batchId,

        @NotNull(message = "Total fee is required")
        @DecimalMin(value = "0.00", message = "Total fee cannot be negative")
        BigDecimal totalFee,

        @DecimalMin(value = "0.00", message = "Discount cannot be negative")
        BigDecimal discount,

        @Schema(description = "Split evenly into this many monthly installments")
        @Min(value = 1) @Max(value = 36)
        Integer installmentCount,

        @Schema(description = "First due date for an even split. Defaults to today.")
        LocalDate firstDueDate,

        @Schema(description = "An explicit schedule, instead of an even split")
        @Valid
        @Size(max = 36)
        List<InstallmentInput> installments,

        @Size(max = 500)
        String notes
) {

    public record InstallmentInput(
            @NotNull(message = "Due date is required")
            LocalDate dueDate,

            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "An installment must be more than zero")
            BigDecimal amount
    ) {
    }
}
