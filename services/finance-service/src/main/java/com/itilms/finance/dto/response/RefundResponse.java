package com.itilms.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.itilms.finance.entity.Refund;

public record RefundResponse(Long id, Long paymentId, Long feePlanId, Long studentId, String studentName,
                             String receiptNo, BigDecimal amount, String reason, String status, Long requestedBy,
                             Instant requestedAt, Long decidedBy, Instant decidedAt, String decisionNote) {

    public static RefundResponse from(Refund r, String studentName, String receiptNo) {
        return new RefundResponse(r.getId(), r.getPaymentId(), r.getFeePlanId(), r.getStudentId(), studentName,
                receiptNo, r.getAmount(), r.getReason(), r.getStatus().name(), r.getCreatedBy(), r.getCreatedAt(),
                r.getDecidedBy(), r.getDecidedAt(), r.getDecisionNote());
    }
}
