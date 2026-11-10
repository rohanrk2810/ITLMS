package com.itilms.finance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Bodies for buying a course online with Razorpay. */
public final class OnlineCheckoutDtos {

    private OnlineCheckoutDtos() {
    }

    @Schema(description = "Whether online payment is set up, and the public key the checkout needs")
    public record CheckoutConfig(boolean enabled, String keyId) {
    }

    public record CreateOrderRequest(@NotNull Long courseId,
                                     @Schema(description = "A batch the student prefers (optional)") Long batchId) {
    }

    @Schema(description = "What the browser needs to open Razorpay Checkout. The amount is the course fee, fixed on the server.")
    public record OrderCreated(String orderId, String keyId, long amountPaise, String currency, String courseTitle) {
    }

    @Schema(description = "What Razorpay Checkout handed back after a successful payment")
    public record ConfirmRequest(@NotBlank @Size(max = 80) String orderId,
                                 @NotBlank @Size(max = 80) String paymentId,
                                 @NotBlank @Size(max = 200) String signature) {
    }

    @Schema(description = "CREATED, PAID (money received, not yet in a batch) or ENROLLED")
    public record OrderStatus(String orderId, String status, Long studentId, Long courseId, Long batchId,
                              String batchCode, String message) {
    }
}
