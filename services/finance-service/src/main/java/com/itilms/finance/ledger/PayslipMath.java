package com.itilms.finance.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.itilms.common.exception.BusinessRuleException;

/**
 * The arithmetic of a salary slip, pure so it can be tested.
 *
 * <p>Gross is basic plus allowances plus bonus. Leave without pay is charged at the day rate of basic plus allowances:
 * that sum divided by the days of the month. Net is gross less the fixed deduction, the leave deduction and any other
 * deduction, and can never be negative.
 */
public final class PayslipMath {

    private PayslipMath() {
    }

    public record Result(BigDecimal lwpDeduction, BigDecimal gross, BigDecimal net) {
    }

    public static Result compute(BigDecimal basic, BigDecimal allowances, BigDecimal fixedDeduction, BigDecimal lwpDays,
                                 int daysInMonth, BigDecimal otherDeduction, BigDecimal bonus) {
        if (lwpDays.signum() < 0 || lwpDays.compareTo(BigDecimal.valueOf(daysInMonth)) > 0) {
            throw new BusinessRuleException("Leave without pay must be between 0 and " + daysInMonth + " days.");
        }
        BigDecimal dayRate = basic.add(allowances).divide(BigDecimal.valueOf(daysInMonth), 6, RoundingMode.HALF_UP);
        BigDecimal lwp = dayRate.multiply(lwpDays).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gross = basic.add(allowances).add(bonus).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(fixedDeduction).subtract(lwp).subtract(otherDeduction);
        if (net.signum() < 0) {
            throw new BusinessRuleException("The deductions are more than the pay, so the net would be below zero.");
        }
        return new Result(lwp, gross, net.setScale(2, RoundingMode.HALF_UP));
    }
}
