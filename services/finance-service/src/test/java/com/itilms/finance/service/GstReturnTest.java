package com.itilms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.itilms.finance.entity.Expense;
import com.itilms.finance.entity.GstPayment;
import com.itilms.finance.ledger.GstReturn;

class GstReturnTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    private static GstReturn.Invoice invoice(String date, String taxable, String cgst, String sgst, String igst) {
        return new GstReturn.Invoice("R", LocalDate.parse(date), "Asha", "Maharashtra", n("18"), n(taxable), BigDecimal.ZERO,
                n(cgst), n(sgst), n(igst), n(taxable).add(n(cgst)).add(n(sgst)).add(n(igst)));
    }

    @Test
    void whatIsPayableIsOutputLessCreditLessWhatWasPaid() {
        Expense rent = Expense.builder().expenseDate(LocalDate.of(2026, 1, 25)).category(Expense.Category.RENT)
                .amount(n("5900")).gstAmount(n("900")).description("Rent").build();
        Expense voided = Expense.builder().expenseDate(LocalDate.of(2026, 1, 26)).category(Expense.Category.RENT)
                .amount(n("1180")).gstAmount(n("180")).description("x").voidedAt(java.time.Instant.now()).build();
        GstPayment paid = GstPayment.builder().paidOn(LocalDate.of(2026, 2, 10)).amount(n("500")).method("BANK_TRANSFER").build();

        var summary = GstReturn.summarise(List.of(invoice("2026-01-10", "10000", "900", "900", "0"),
                invoice("2026-01-20", "5000", "0", "0", "900")), List.of(rent, voided), List.of(paid));

        assertThat(summary.months()).hasSize(2);
        assertThat(summary.months().get(0).outputTax()).isEqualByComparingTo("2700");
        assertThat(summary.months().get(0).inputCredit()).isEqualByComparingTo("900");
        assertThat(summary.months().get(0).netPayable()).isEqualByComparingTo("1800");
        assertThat(summary.months().get(1).paid()).isEqualByComparingTo("500");
        assertThat(summary.stillPayable()).isEqualByComparingTo("1300");
    }
}
