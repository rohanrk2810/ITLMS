package com.itilms.finance.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Ask for money to be given back on a payment")
public record RefundRequest(
        @NotNull(message = "Choose the payment") Long paymentId,
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be more than zero")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most two decimal places") BigDecimal amount,
        @NotBlank(message = "A reason is required") @Size(max = 255) String reason) {
}
