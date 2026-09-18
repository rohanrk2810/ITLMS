package com.itilms.finance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/** One overdue installment - a row on the finance desk's chase list. */
@Schema(description = "An installment past its due date and not fully paid")
public record OverdueItemResponse(
        Long feePlanId,
        Long installmentId,
        Long studentId,
        String studentCode,
        String studentName,
        int installmentNo,
        LocalDate dueDate,
        long daysOverdue,
        @Schema(description = "What is unpaid on this installment, not its full value")
        BigDecimal amountOverdue
) {
}
