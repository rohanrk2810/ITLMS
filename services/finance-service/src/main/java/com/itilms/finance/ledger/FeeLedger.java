package com.itilms.finance.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.itilms.finance.entity.FeeInstallment;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.Payment;

/**
 * Where a fee plan stands on a given day, worked out from its payments.
 *
 * <p>This is the one place IT-ILMS does fee arithmetic. Nothing it produces is
 * stored: the outstanding amount, which installments are paid, which are
 * overdue - all are recomputed from the plan and its payments whenever they are
 * needed. A reversed cheque therefore changes every figure the moment it is
 * reversed, with no balance column left behind to correct.
 *
 * <p>Two rules:
 * <ol>
 *   <li><b>Outstanding = net fee − successful payments</b> (Doc S14), never
 *       less than zero.</li>
 *   <li>Money received is applied to installments <b>oldest due date first</b>.
 *       A student who pays ₹8,000 against two ₹5,000 installments has cleared
 *       the first and part of the second - not half of each - because that is
 *       what "you are behind" means to both the student and the accountant.</li>
 * </ol>
 *
 * <p>Pure and side-effect free, so the rules can be tested without a database.
 */
public final class FeeLedger {

    /** Where one installment stands. */
    public enum InstallmentState {
        PAID,
        /** Some money applied, due date not yet passed. */
        PARTLY_PAID,
        /** Due today, nothing or not enough applied. */
        DUE,
        /** Due date passed and not fully paid - partly paid counts as overdue. */
        OVERDUE,
        /** Not due yet and nothing applied. */
        UPCOMING
    }

    public record Line(FeeInstallment installment, BigDecimal paid, BigDecimal remaining, InstallmentState state) {

        public boolean isOpen() {
            return state != InstallmentState.PAID;
        }
    }

    private final BigDecimal netFee;
    private final BigDecimal paid;
    private final BigDecimal outstanding;
    private final BigDecimal overdueAmount;
    private final List<Line> lines;

    private FeeLedger(BigDecimal netFee, BigDecimal paid, BigDecimal outstanding,
                      BigDecimal overdueAmount, List<Line> lines) {
        this.netFee = netFee;
        this.paid = paid;
        this.outstanding = outstanding;
        this.overdueAmount = overdueAmount;
        this.lines = lines;
    }

    public static FeeLedger of(FeePlan plan, List<Payment> payments, LocalDate today) {
        BigDecimal paid = payments.stream()
                .filter(Payment::counts)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FeeInstallment> ordered = plan.getInstallments().stream()
                .sorted(Comparator.comparing(FeeInstallment::getDueDate)
                        .thenComparingInt(FeeInstallment::getInstallmentNo))
                .toList();

        BigDecimal pool = paid;
        BigDecimal overdue = BigDecimal.ZERO;
        List<Line> lines = new ArrayList<>(ordered.size());

        for (FeeInstallment installment : ordered) {
            BigDecimal applied = pool.min(installment.getAmount());
            pool = pool.subtract(applied);
            BigDecimal remaining = installment.getAmount().subtract(applied);

            InstallmentState state = stateOf(installment, applied, remaining, today);
            if (state == InstallmentState.OVERDUE) {
                overdue = overdue.add(remaining);
            }
            lines.add(new Line(installment, applied, remaining, state));
        }

        BigDecimal outstanding = plan.getNetFee().subtract(paid).max(BigDecimal.ZERO);
        return new FeeLedger(plan.getNetFee(), paid, outstanding, overdue, List.copyOf(lines));
    }

    private static InstallmentState stateOf(FeeInstallment installment, BigDecimal applied,
                                            BigDecimal remaining, LocalDate today) {
        if (remaining.signum() <= 0) {
            return InstallmentState.PAID;
        }
        if (installment.getDueDate().isBefore(today)) {
            return InstallmentState.OVERDUE;
        }
        if (installment.getDueDate().isEqual(today)) {
            return InstallmentState.DUE;
        }
        return applied.signum() > 0 ? InstallmentState.PARTLY_PAID : InstallmentState.UPCOMING;
    }

    public BigDecimal netFee() {
        return netFee;
    }

    public BigDecimal paid() {
        return paid;
    }

    /** Doc S14: net fee minus successful payments, never negative. */
    public BigDecimal outstanding() {
        return outstanding;
    }

    public BigDecimal overdueAmount() {
        return overdueAmount;
    }

    public List<Line> lines() {
        return lines;
    }

    public boolean isSettled() {
        return outstanding.signum() == 0;
    }

    /** The earliest installment still owing, or null when nothing is. */
    public Line nextDue() {
        return lines.stream().filter(Line::isOpen).findFirst().orElse(null);
    }

    public Line lineFor(Long installmentId) {
        return lines.stream().filter(l -> l.installment().getId() != null
                && l.installment().getId().equals(installmentId)).findFirst().orElse(null);
    }
}
