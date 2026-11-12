package com.itilms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.PaymentMethod;

class GstReportServiceTest {

    private static Payment payment(String date, String amount, String refunded) {
        return Payment.builder().feePlanId(1L).studentId(1L).amount(new BigDecimal(amount))
                .refundedAmount(new BigDecimal(refunded)).paymentDate(LocalDate.parse(date))
                .method(PaymentMethod.CASH).receiptNo("R").build();
    }

    @Test
    void addsUpTheTaxPerMonthNetOfRefunds() {
        var report = GstReportService.summarise(new BigDecimal("18"), "27AAAAA0000A1Z5", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 28), List.of(payment("2026-01-10", "11800", "0"), payment("2026-01-20", "5900", "0"),
                        payment("2026-02-05", "11800", "5900")));

        assertThat(report.months()).hasSize(2);
        assertThat(report.months().get(0).taxable()).isEqualByComparingTo("15000.00");
        assertThat(report.months().get(0).cgst()).isEqualByComparingTo("1350.00");
        assertThat(report.months().get(1).received()).isEqualByComparingTo("5900.00");
        assertThat(report.taxable().add(report.cgst()).add(report.sgst())).isEqualByComparingTo(report.received());
    }
}
