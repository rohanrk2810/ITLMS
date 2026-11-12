package com.itilms.finance.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.PaymentMethod;

class RefundLedgerTest {

    @Test
    void aRefundedPaymentIsOwedAgain() {
        FeePlan plan = FeePlan.builder().studentId(1L).courseId(1L).build();
        plan.setTerms(new BigDecimal("10000"), BigDecimal.ZERO);
        plan.replaceInstallments(new ArrayList<>(ScheduleBuilder.evenSplit(plan.getNetFee(), 1, LocalDate.of(2026, 1, 1))));
        Payment payment = Payment.builder().feePlanId(1L).studentId(1L).amount(new BigDecimal("10000.00"))
                .paymentDate(LocalDate.of(2026, 1, 1)).method(PaymentMethod.CASH).receiptNo("R").build();

        assertThat(FeeLedger.of(plan, List.of(payment), LocalDate.of(2026, 1, 5)).isSettled()).isTrue();

        payment.setRefundedAmount(new BigDecimal("4000.00"));
        FeeLedger after = FeeLedger.of(plan, List.of(payment), LocalDate.of(2026, 1, 5));

        assertThat(after.paid()).isEqualByComparingTo("6000");
        assertThat(after.outstanding()).isEqualByComparingTo("4000");
        assertThat(payment.netAmount()).isEqualByComparingTo("6000");
    }
}
