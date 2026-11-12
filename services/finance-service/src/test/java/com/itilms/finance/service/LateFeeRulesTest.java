package com.itilms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import com.itilms.common.event.EventPublisher;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.FeeInstallment;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.ledger.ScheduleBuilder;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;

/** A late fee is charged once, after the grace days, and never on a late fee. */
class LateFeeRulesTest {

    private static InstallmentReminderJob job(String lateFee) {
        FinanceProperties props = new FinanceProperties();
        props.setLateFee(new BigDecimal(lateFee));
        props.setLateFeeAfterDays(7);
        return new InstallmentReminderJob(mock(FeePlanRepository.class), mock(PaymentRepository.class), props,
                mock(EventPublisher.class));
    }

    private static FeeInstallment installment() {
        return FeeInstallment.builder().installmentNo(1).dueDate(LocalDate.of(2026, 1, 1))
                .amount(new BigDecimal("1000")).build();
    }

    @Test
    void isOffWhenTheAmountIsZero() {
        assertThat(job("0").lateFeeDue(installment(), 30)).isFalse();
    }

    @Test
    void waitsForTheGraceDays() {
        assertThat(job("100").lateFeeDue(installment(), 7)).isFalse();
        assertThat(job("100").lateFeeDue(installment(), 8)).isTrue();
    }

    @Test
    void isChargedOnlyOnce() {
        FeeInstallment charged = installment();
        charged.setLateFeeChargedAt(Instant.now());

        assertThat(job("100").lateFeeDue(charged, 30)).isFalse();
    }

    @Test
    void neverChargesALateFeeOnALateFee() {
        FeeInstallment late = installment();
        late.setLabel(FeeInstallment.LATE_FEE_LABEL);

        assertThat(job("100").lateFeeDue(late, 30)).isFalse();
    }

    @Test
    void aWaivedLateFeeShrinksTheTotal() {
        FeePlan plan = FeePlan.builder().studentId(1L).courseId(1L).build();
        plan.setTerms(new BigDecimal("1000"), BigDecimal.ZERO);
        plan.replaceInstallments(new ArrayList<>(ScheduleBuilder.evenSplit(plan.getNetFee(), 1, LocalDate.of(2026, 1, 1))));
        FeeInstallment fee = plan.addCharge(FeeInstallment.LATE_FEE_LABEL, new BigDecimal("100"), LocalDate.of(2026, 2, 1));
        assertThat(plan.getNetFee()).isEqualByComparingTo("1100");

        plan.removeCharge(fee);

        assertThat(plan.getNetFee()).isEqualByComparingTo("1000");
        assertThat(plan.getInstallments()).hasSize(1);
    }
}
