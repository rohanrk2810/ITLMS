package com.itilms.finance.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class GstMathTest {

    @Test
    void takesTheTaxOutOfATaxInclusiveFee() {
        var split = GstMath.split(new BigDecimal("11800"), new BigDecimal("18"));

        assertThat(split.taxable()).isEqualByComparingTo("10000.00");
        assertThat(split.cgst()).isEqualByComparingTo("900.00");
        assertThat(split.sgst()).isEqualByComparingTo("900.00");
        assertThat(split.tax()).isEqualByComparingTo("1800.00");
    }

    @Test
    void theThreePartsAlwaysAddBackToTheAmount() {
        var split = GstMath.split(new BigDecimal("1001"), new BigDecimal("18"));

        assertThat(split.taxable().add(split.cgst()).add(split.sgst())).isEqualByComparingTo("1001.00");
    }

    @Test
    void noRateMeansNoTax() {
        var split = GstMath.split(new BigDecimal("5000"), BigDecimal.ZERO);

        assertThat(split.taxable()).isEqualByComparingTo("5000");
        assertThat(split.tax()).isEqualByComparingTo("0");
    }
}
