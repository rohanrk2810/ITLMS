package com.itilms.finance.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.itilms.common.exception.BusinessRuleException;

class PayslipMathTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    @Test
    void netIsGrossLessEveryDeduction() {
        var r = PayslipMath.compute(n("30000"), n("5000"), n("1000"), n("0"), 30, n("500"), n("2000"));

        assertThat(r.gross()).isEqualByComparingTo("37000");
        assertThat(r.net()).isEqualByComparingTo("35500");
    }

    @Test
    void leaveWithoutPayIsChargedAtTheDayRate() {
        // 36000 over a 30 day month is 1200 a day; 2.5 days is 3000.
        var r = PayslipMath.compute(n("30000"), n("6000"), n("0"), n("2.5"), 30, n("0"), n("0"));

        assertThat(r.lwpDeduction()).isEqualByComparingTo("3000");
        assertThat(r.net()).isEqualByComparingTo("33000");
    }

    @Test
    void theNetCannotGoBelowZero() {
        assertThatThrownBy(() -> PayslipMath.compute(n("1000"), n("0"), n("0"), n("0"), 30, n("1500"), n("0")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void moreLeaveThanDaysInTheMonthIsRefused() {
        assertThatThrownBy(() -> PayslipMath.compute(n("1000"), n("0"), n("0"), n("31"), 30, n("0"), n("0")))
                .isInstanceOf(BusinessRuleException.class);
    }
}
