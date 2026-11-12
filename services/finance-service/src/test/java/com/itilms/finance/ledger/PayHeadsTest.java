package com.itilms.finance.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.itilms.common.exception.BusinessRuleException;

class PayHeadsTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    private static PayHeads.Head earn(String name, String amount, boolean basic) {
        return new PayHeads.Head("EARNING", name, n(amount), null, basic);
    }

    @Test
    void addsUpEarningsAndDeductionsAndFindsTheTotals() {
        var r = PayHeads.resolve(List.of(earn("Base Salary", "52298", true), earn("House Rent Allowance", "36609", false),
                earn("Shift Allowance", "4050", false), new PayHeads.Head("DEDUCTION", "Profession Tax", n("200"), null, false)));

        assertThat(r.basic()).isEqualByComparingTo("52298");
        assertThat(r.allowances()).isEqualByComparingTo("40659");
        assertThat(r.deductions()).isEqualByComparingTo("200");
        assertThat(r.lines()).hasSize(4);
    }

    @Test
    void aPercentHeadIsAShareOfTheBasic() {
        var r = PayHeads.resolve(List.of(earn("Basic", "50000", true),
                new PayHeads.Head("DEDUCTION", "Provident Fund", null, n("12"), false)));

        assertThat(r.lines().get(1).amount()).isEqualByComparingTo("6000");
        assertThat(r.deductions()).isEqualByComparingTo("6000");
    }

    @Test
    void refusesNothingToPayDeductionsAboveEarningsAndBadHeads() {
        assertThatThrownBy(() -> PayHeads.resolve(List.of())).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PayHeads.resolve(List.of(earn("Basic", "1000", true),
                new PayHeads.Head("DEDUCTION", "TDS", n("2000"), null, false)))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PayHeads.resolve(List.of(earn(" ", "1000", true)))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PayHeads.resolve(List.of(earn("Basic", "1000", true),
                new PayHeads.Head("DEDUCTION", "PF", null, n("150"), false)))).isInstanceOf(BusinessRuleException.class);
    }
}
