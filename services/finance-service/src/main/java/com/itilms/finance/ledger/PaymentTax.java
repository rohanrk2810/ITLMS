package com.itilms.finance.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The GST in one payment. Pure, so it can be tested.
 *
 * <p>The fee is taken as tax-inclusive. Each fee head the payment went towards is taxed at the rate unless it is marked
 * as exempt; whatever was paid in advance of any head is taxed. A payment to a student in another state than the
 * institute's carries IGST in place of CGST and SGST. A rate of zero means no tax at all.
 */
public final class PaymentTax {

    private PaymentTax() {
    }

    public record Result(BigDecimal rate, BigDecimal taxable, BigDecimal exempt, BigDecimal cgst, BigDecimal sgst,
                         BigDecimal igst, boolean interState) {

        public BigDecimal tax() {
            return cgst.add(sgst).add(igst);
        }

        /** The same split for a part of the payment (what is left after a refund), each figure scaled and rounded. */
        public Result scaled(BigDecimal part, BigDecimal whole) {
            if (whole.signum() == 0 || part.compareTo(whole) == 0) {
                return this;
            }
            BigDecimal f = part.divide(whole, 10, RoundingMode.HALF_UP);
            return new Result(rate, scale(taxable, f), scale(exempt, f), scale(cgst, f), scale(sgst, f), scale(igst, f), interState);
        }

        private static BigDecimal scale(BigDecimal v, BigDecimal f) {
            return v.multiply(f).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /** A slice of the payment: how much went to a fee head, and whether that head carries GST. */
    public record Part(BigDecimal amount, boolean exempt) {
    }

    public static Result compute(BigDecimal payment, List<Part> parts, BigDecimal ratePercent, boolean interState) {
        BigDecimal rate = ratePercent == null ? BigDecimal.ZERO : ratePercent;
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        BigDecimal gross = payment.setScale(2, RoundingMode.HALF_UP);
        if (rate.signum() <= 0) {
            return new Result(BigDecimal.ZERO, gross, zero, zero, zero, zero, interState);
        }
        BigDecimal exempt = zero;
        BigDecimal taxableBase = zero;
        BigDecimal placed = zero;
        for (Part part : parts) {
            placed = placed.add(part.amount());
            if (part.exempt()) {
                exempt = exempt.add(part.amount());
            } else {
                taxableBase = taxableBase.add(part.amount());
            }
        }
        // Money paid ahead of any fee head is taxed like the rest.
        BigDecimal ahead = gross.subtract(placed);
        if (ahead.signum() > 0) {
            taxableBase = taxableBase.add(ahead);
        }
        GstMath.Split split = GstMath.split(taxableBase, rate);
        BigDecimal tax = split.tax();
        if (interState) {
            return new Result(rate, split.taxable(), exempt, zero, zero, tax, true);
        }
        return new Result(rate, split.taxable(), exempt, split.cgst(), split.sgst(), zero, false);
    }

    /** Whether a supply to a student in this state is inter-state, given the institute's GSTIN. Unknown means intra-state. */
    public static boolean interState(String studentState, String gstin) {
        String student = StateCodes.codeOf(studentState);
        if (student == null || gstin == null || gstin.trim().length() < 2) {
            return false;
        }
        return !student.equals(gstin.trim().substring(0, 2));
    }
}
