package com.itilms.finance.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

@Schema(description = "Money received against a fee plan (Doc S6.12)")
public record RecordPaymentRequest(

        @NotNull(message = "Fee plan is required")
        Long feePlanId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be more than zero")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most two decimal places")
        BigDecimal amount,

        @Schema(description = "Defaults to today")
        @PastOrPresent(message = "A payment cannot be dated in the future")
        LocalDate paymentDate,

        @NotBlank(message = "Payment method is required")
        @Schema(description = "CASH, UPI, CARD, BANK_TRANSFER, CHEQUE or ONLINE")
        String method,

        @Schema(description = "Transaction id, cheque number or bank reference. Required except for cash.")
        @Size(max = 80)
        String referenceNo,

        @Size(max = 500)
        String notes
) {
}
