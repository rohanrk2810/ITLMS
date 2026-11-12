package com.itilms.finance.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Goods and services tax on a fee that is quoted <em>including</em> tax, the way a student is told "the course is 20,000".
 * The taxable value is the amount divided by one plus the rate; the tax is the rest, shared equally between the central
 * and the state part (CGST and SGST).
 */
public final class GstMath {

    private GstMath() {
    }

    public record Split(BigDecimal taxable, BigDecimal cgst, BigDecimal sgst) {
        public BigDecimal tax() {
            return cgst.add(sgst);
        }
    }

    /** Splits a tax-inclusive amount. With a rate of zero the whole amount is taxable and there is no tax. */
    public static Split split(BigDecimal amount, BigDecimal ratePercent) {
        BigDecimal rate = ratePercent == null ? BigDecimal.ZERO : ratePercent;
        BigDecimal gross = amount.setScale(2, RoundingMode.HALF_UP);
        if (rate.signum() <= 0) {
            return new Split(gross, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
        }
        BigDecimal taxable = gross.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(100).add(rate), 2, RoundingMode.HALF_UP);
        BigDecimal tax = gross.subtract(taxable);
        BigDecimal cgst = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return new Split(taxable, cgst, tax.subtract(cgst));
    }
}
