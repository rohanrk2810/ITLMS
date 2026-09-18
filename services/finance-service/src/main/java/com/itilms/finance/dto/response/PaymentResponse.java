package com.itilms.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.itilms.finance.entity.Payment;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A payment received")
public record PaymentResponse(
        Long id,
        Long feePlanId,
        Long studentId,
        BigDecimal amount,
        LocalDate paymentDate,
        String method,
        String referenceNo,
        String receiptNo,
        @Schema(description = "SUCCESS, or REVERSED for a bounced or mistaken payment")
        String status,
        Instant reversedAt,
        String reversalReason,
        String notes,
        Instant recordedAt
) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getFeePlanId(), p.getStudentId(), p.getAmount(),
                p.getPaymentDate(), p.getMethod().name(), p.getReferenceNo(), p.getReceiptNo(),
                p.getStatus().name(), p.getReversedAt(), p.getReversalReason(), p.getNotes(), p.getCreatedAt());
    }
}
