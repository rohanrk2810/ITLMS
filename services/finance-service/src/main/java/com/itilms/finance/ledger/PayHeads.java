package com.itilms.finance.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.itilms.common.exception.BusinessRuleException;

/**
 * Turns the heads of a person's pay into amounts and the three totals the slip arithmetic needs.
 *
 * <p>A head is an earning or a deduction, a fixed amount or a percent of the basic (provident fund is usually 12% of
 * basic). The basic is the earning flagged as such. Pure, so it can be tested.
 */
public final class PayHeads {

    private PayHeads() {
    }

    public record Head(String kind, String name, BigDecimal amount, BigDecimal percentOfBasic, boolean basic) {
    }

    public record Line(String kind, String name, BigDecimal amount) {
    }

    /** The heads with their amounts, and the totals: basic, other earnings, and all deductions. */
    public record Resolved(List<Line> lines, BigDecimal basic, BigDecimal allowances, BigDecimal deductions) {

        public BigDecimal earnings() {
            return basic.add(allowances);
        }
    }

    public static Resolved resolve(List<Head> heads) {
        if (heads == null || heads.isEmpty()) {
            throw new BusinessRuleException("Add at least one earning, such as the basic pay.");
        }
        BigDecimal basic = BigDecimal.ZERO;
        for (Head h : heads) {
            validate(h);
            if (h.basic() && "EARNING".equals(h.kind())) {
                basic = basic.add(money(h.amount()));
            }
        }
        List<Line> lines = new ArrayList<>();
        BigDecimal earnings = BigDecimal.ZERO;
        BigDecimal deductions = BigDecimal.ZERO;
        for (Head h : heads) {
            BigDecimal amount = h.percentOfBasic() != null
                    ? basic.multiply(h.percentOfBasic()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : money(h.amount());
            lines.add(new Line(h.kind(), h.name().trim(), amount));
            if ("EARNING".equals(h.kind())) {
                earnings = earnings.add(amount);
            } else {
                deductions = deductions.add(amount);
            }
        }
        if (earnings.signum() <= 0) {
            throw new BusinessRuleException("The earnings add up to nothing. Give the basic pay an amount.");
        }
        if (deductions.compareTo(earnings) > 0) {
            throw new BusinessRuleException("The deductions are more than the earnings.");
        }
        return new Resolved(lines, basic, earnings.subtract(basic), deductions);
    }

    private static void validate(Head h) {
        if (!"EARNING".equals(h.kind()) && !"DEDUCTION".equals(h.kind())) {
            throw new BusinessRuleException("Each head is an earning or a deduction.");
        }
        if (h.name() == null || h.name().isBlank() || h.name().trim().length() > 120) {
            throw new BusinessRuleException("Every head needs a name (up to 120 characters).");
        }
        if (h.percentOfBasic() != null) {
            if (h.percentOfBasic().signum() <= 0 || h.percentOfBasic().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BusinessRuleException("A percent of the basic must be between 0 and 100.");
            }
            if (h.basic()) {
                throw new BusinessRuleException("The basic itself cannot be a percent of the basic.");
            }
        } else if (h.amount() == null || h.amount().signum() < 0) {
            throw new BusinessRuleException("An amount cannot be negative.");
        }
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }
}
