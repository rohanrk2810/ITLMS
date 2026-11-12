package com.itilms.finance.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class PaymentTaxTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    @Test
    void splitsAnOrdinaryFeeIntoCgstAndSgst() {
        var r = PaymentTax.compute(n("11800"), List.of(new PaymentTax.Part(n("11800"), false)), n("18"), false);

        assertThat(r.taxable()).isEqualByComparingTo("10000");
        assertThat(r.cgst()).isEqualByComparingTo("900");
        assertThat(r.sgst()).isEqualByComparingTo("900");
        assertThat(r.igst()).isEqualByComparingTo("0");
    }

    @Test
    void anOutOfStateStudentPaysIgst() {
        var r = PaymentTax.compute(n("11800"), List.of(new PaymentTax.Part(n("11800"), false)), n("18"), true);

        assertThat(r.igst()).isEqualByComparingTo("1800");
        assertThat(r.cgst()).isEqualByComparingTo("0");
        assertThat(PaymentTax.interState("Karnataka", "27ABCDE1234F1Z5")).isTrue();
        assertThat(PaymentTax.interState("Maharashtra", "27ABCDE1234F1Z5")).isFalse();
        assertThat(PaymentTax.interState("", "27ABCDE1234F1Z5")).isFalse();
    }

    @Test
    void anExemptHeadIsNotTaxed() {
        var r = PaymentTax.compute(n("13800"), List.of(new PaymentTax.Part(n("11800"), false), new PaymentTax.Part(n("2000"), true)),
                n("18"), false);

        assertThat(r.exempt()).isEqualByComparingTo("2000");
        assertThat(r.taxable()).isEqualByComparingTo("10000");
        assertThat(r.tax()).isEqualByComparingTo("1800");
    }

    @Test
    void moneyPaidAheadIsTaxedAndNoRateMeansNoTax() {
        assertThat(PaymentTax.compute(n("11800"), List.of(), n("18"), false).tax()).isEqualByComparingTo("1800");
        var none = PaymentTax.compute(n("5000"), List.of(new PaymentTax.Part(n("5000"), false)), BigDecimal.ZERO, false);
        assertThat(none.tax()).isEqualByComparingTo("0");
        assertThat(none.taxable()).isEqualByComparingTo("5000");
    }

    @Test
    void aRefundShrinksEveryFigureInProportion() {
        var gross = PaymentTax.compute(n("11800"), List.of(new PaymentTax.Part(n("11800"), false)), n("18"), false);

        var half = gross.scaled(n("5900"), n("11800"));

        assertThat(half.taxable()).isEqualByComparingTo("5000");
        assertThat(half.cgst()).isEqualByComparingTo("450");
    }

    @Test
    void knowsTheStateCodes() {
        assertThat(StateCodes.codeOf("Maharashtra")).isEqualTo("27");
        assertThat(StateCodes.codeOf(" TELANGANA ")).isEqualTo("36");
        assertThat(StateCodes.codeOf("Jammu & Kashmir")).isEqualTo("01");
        assertThat(StateCodes.codeOf("Atlantis")).isNull();
        assertThat(StateCodes.nameOf("27")).isEqualTo("Maharashtra");
    }
}
