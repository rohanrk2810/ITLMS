package com.itilms.admission.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Turn a lead into an admitted student (Doc S7.1).
 *
 * <p>This one call sets off the whole admission chain: a user account, a student
 * profile, a batch enrolment, and a fee plan. Grouping it into a single request
 * is deliberate — a coordinator should not be able to leave an admission half
 * finished with a student who exists but owes nothing.
 */
@Schema(description = "Convert a lead into an admitted student")
public record ConvertLeadRequest(

        @Schema(description = "Course the student is admitted to")
        @NotNull(message = "Course is required")
        @Positive
        Long courseId,

        @Schema(description = "Batch to enrol into. May be left empty and assigned later.")
        Long batchId,

        @Schema(description = "Agreed total fee before discount")
        @NotNull(message = "Total fee is required")
        @DecimalMin(value = "0.0", message = "Fee cannot be negative")
        BigDecimal totalFee,

        @Schema(description = "Discount agreed at admission")
        @DecimalMin(value = "0.0", message = "Discount cannot be negative")
        BigDecimal discount,

        @Schema(description = "How many installments to split the net fee into", example = "3")
        Integer installments,

        @Schema(description = "Defaults to today")
        LocalDate admissionDate
) {

    public BigDecimal discountOrZero() {
        return discount == null ? BigDecimal.ZERO : discount;
    }

    public int installmentsOrOne() {
        return installments == null || installments < 1 ? 1 : installments;
    }

    public LocalDate admissionDateOrToday() {
        return admissionDate == null ? LocalDate.now() : admissionDate;
    }
}
