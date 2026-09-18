package com.itilms.finance.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.itilms.finance.entity.FeeInstallment;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.PaymentMethod;
import com.itilms.finance.entity.PaymentStatus;
import com.itilms.finance.ledger.FeeLedger.InstallmentState;

/**
 * The fee arithmetic a student or an accountant could dispute.
 *
 * <p>A ₹30,000 plan in three ₹10,000 installments due on the 1st of Jan, Feb
 * and Mar, looked at on 15 Feb unless a test says otherwise.
 */
class FeeLedgerTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate MID_FEB = LocalDate.of(2026, 2, 15);

    private static BigDecimal rs(long rupees) {
        return BigDecimal.valueOf(rupees).setScale(2);
    }

    private static FeePlan plan() {
        FeePlan plan = FeePlan.builder().studentId(1L).courseId(1L).build();
        plan.setTerms(rs(30_000), BigDecimal.ZERO);
        plan.replaceInstallments(new ArrayList<>(ScheduleBuilder.evenSplit(plan.getNetFee(), 3, JAN_1)));
        return plan;
    }

    private static Payment paid(long rupees) {
        return Payment.builder().feePlanId(1L).studentId(1L).amount(rs(rupees))
                .paymentDate(JAN_1).method(PaymentMethod.CASH).receiptNo("R").build();
    }

    private static Payment reversed(long rupees) {
        Payment p = paid(rupees);
        p.setStatus(PaymentStatus.REVERSED);
        return p;
    }

    @Nested
    @DisplayName("Outstanding amount (Doc S14)")
    class Outstanding {

        @Test
        @DisplayName("Net fee minus successful payments")
        void formula() {
            FeeLedger ledger = FeeLedger.of(plan(), List.of(paid(12_000)), MID_FEB);
            assertThat(ledger.paid()).isEqualByComparingTo("12000");
            assertThat(ledger.outstanding()).isEqualByComparingTo("18000");
        }

        @Test
        @DisplayName("A reversed payment does not count - a bounced cheque is not money")
        void reversedIgnored() {
            FeeLedger ledger = FeeLedger.of(plan(), List.of(paid(10_000), reversed(10_000)), MID_FEB);
            assertThat(ledger.outstanding()).isEqualByComparingTo("20000");
        }

        @Test
        @DisplayName("Fully paid means settled, and outstanding never goes negative")
        void settled() {
            FeeLedger ledger = FeeLedger.of(plan(), List.of(paid(30_000)), MID_FEB);
            assertThat(ledger.isSettled()).isTrue();
            assertThat(ledger.outstanding()).isEqualByComparingTo("0");
            assertThat(ledger.nextDue()).isNull();
        }

        @Test
        @DisplayName("A discount reduces the net fee")
        void discount() {
            FeePlan plan = FeePlan.builder().studentId(1L).courseId(1L).build();
            plan.setTerms(rs(30_000), rs(5_000));
            assertThat(plan.getNetFee()).isEqualByComparingTo("25000");
        }
    }

    @Nested
    @DisplayName("Applying money to installments, oldest first")
    class Allocation {

        @Test
        @DisplayName("₹12,000 clears January and part of February, not a third of each")
        void oldestFirst() {
            List<FeeLedger.Line> lines = FeeLedger.of(plan(), List.of(paid(12_000)), MID_FEB).lines();

            assertThat(lines.get(0).state()).isEqualTo(InstallmentState.PAID);
            assertThat(lines.get(1).paid()).isEqualByComparingTo("2000");
            assertThat(lines.get(1).remaining()).isEqualByComparingTo("8000");
            assertThat(lines.get(2).paid()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("A partly paid installment past its date is overdue for the unpaid part")
        void partlyPaidOverdue() {
            FeeLedger ledger = FeeLedger.of(plan(), List.of(paid(12_000)), MID_FEB);

            assertThat(ledger.lines().get(1).state()).isEqualTo(InstallmentState.OVERDUE);
            assertThat(ledger.overdueAmount()).isEqualByComparingTo("8000");
            assertThat(ledger.lines().get(2).state()).isEqualTo(InstallmentState.UPCOMING);
        }

        @Test
        @DisplayName("Paying ahead covers future installments")
        void payingAhead() {
            FeeLedger ledger = FeeLedger.of(plan(), List.of(paid(25_000)), MID_FEB);

            assertThat(ledger.lines().get(2).state()).isEqualTo(InstallmentState.PARTLY_PAID);
            assertThat(ledger.overdueAmount()).isEqualByComparingTo("0");
            assertThat(ledger.nextDue().installment().getInstallmentNo()).isEqualTo(3);
        }

        @Test
        @DisplayName("An installment falling due today is DUE, not yet overdue")
        void dueToday() {
            FeeLedger ledger = FeeLedger.of(plan(), List.of(paid(10_000)), LocalDate.of(2026, 2, 1));
            assertThat(ledger.lines().get(1).state()).isEqualTo(InstallmentState.DUE);
        }
    }

    @Nested
    @DisplayName("Splitting a fee into installments")
    class Splitting {

        @Test
        @DisplayName("Whole rupees, with the remainder on the last installment")
        void remainderLast() {
            List<FeeInstallment> schedule = ScheduleBuilder.evenSplit(rs(50_000), 3, JAN_1);

            assertThat(schedule).extracting(FeeInstallment::getAmount)
                    .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .containsExactly(rs(16_666), rs(16_666), rs(16_668));
            assertThat(ScheduleBuilder.total(schedule)).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("Installments fall due a month apart")
        void monthly() {
            List<FeeInstallment> schedule = ScheduleBuilder.evenSplit(rs(30_000), 3, LocalDate.of(2026, 1, 31));
            assertThat(schedule).extracting(FeeInstallment::getDueDate)
                    .containsExactly(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31));
        }
    }
}
