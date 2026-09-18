package com.itilms.finance.dto.response;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** The finance dashboard (Doc S15: "Total billed, collected, pending, overdue installments, payment method split"). */
@Schema(description = "Fee position across the institute")
public record FinanceDashboardResponse(
        String currency,
        @Schema(description = "Net fees on every plan that has not been cancelled")
        BigDecimal totalBilled,
        BigDecimal collected,
        @Schema(description = "Billed minus collected")
        BigDecimal pending,
        BigDecimal overdueAmount,
        int overdueInstallments,
        int studentsWithOverdue,
        long activePlans,
        long settledPlans,
        BigDecimal collectedThisMonth,
        @Schema(description = "This month's collections, by method")
        List<MethodTotal> methodSplitThisMonth
) {

    public record MethodTotal(String method, long payments, BigDecimal amount) {
    }
}
