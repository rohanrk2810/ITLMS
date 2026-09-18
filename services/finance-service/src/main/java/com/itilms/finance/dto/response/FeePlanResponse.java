package com.itilms.finance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.ledger.FeeLedger;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A fee plan with every figure worked out as of today.
 *
 * <p>Paid, outstanding and overdue are computed from the payments on each
 * request rather than stored, so this is always consistent with the payment
 * list beside it.
 */
@Schema(description = "A fee plan, its schedule and where it stands")
public record FeePlanResponse(
        Long id,
        Long studentId,
        String studentCode,
        String studentName,
        Long courseId,
        Long batchId,
        String currency,
        BigDecimal totalFee,
        BigDecimal discount,
        BigDecimal netFee,
        BigDecimal paid,
        @Schema(description = "Net fee minus successful payments (Doc S14)")
        BigDecimal outstanding,
        @Schema(description = "The unpaid part of every installment past its due date")
        BigDecimal overdue,
        String status,
        LocalDate nextDueDate,
        BigDecimal nextDueAmount,
        String notes,
        List<InstallmentResponse> installments,
        @Schema(description = "Present on the single-plan and student views")
        List<PaymentResponse> payments
) {

    @Schema(description = "One installment and how much of it is paid")
    public record InstallmentResponse(
            Long id,
            int installmentNo,
            LocalDate dueDate,
            BigDecimal amount,
            BigDecimal paid,
            BigDecimal remaining,
            @Schema(description = "PAID, PARTLY_PAID, DUE, OVERDUE or UPCOMING")
            String state
    ) {
    }

    public static FeePlanResponse of(FeePlan plan, FeeLedger ledger, List<PaymentResponse> payments) {
        FeeLedger.Line next = ledger.nextDue();
        return new FeePlanResponse(
                plan.getId(), plan.getStudentId(), plan.getStudentCode(), plan.getStudentName(),
                plan.getCourseId(), plan.getBatchId(), plan.getCurrency(),
                plan.getTotalFee(), plan.getDiscount(), plan.getNetFee(),
                ledger.paid(), ledger.outstanding(), ledger.overdueAmount(), plan.getStatus().name(),
                next == null ? null : next.installment().getDueDate(),
                next == null ? null : next.remaining(),
                plan.getNotes(),
                ledger.lines().stream().map(l -> new InstallmentResponse(
                        l.installment().getId(), l.installment().getInstallmentNo(),
                        l.installment().getDueDate(), l.installment().getAmount(),
                        l.paid(), l.remaining(), l.state().name())).toList(),
                payments);
    }
}
