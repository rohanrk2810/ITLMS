package com.itilms.finance.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.itilms.finance.entity.FeeInstallment;

/**
 * Splits a net fee into installments.
 *
 * <p>Amounts are whole rupees, with the remainder on the last installment:
 * ₹50,000 in three becomes 16,666 + 16,666 + 16,668. Paise on an installment
 * are a nuisance to collect in cash and to explain on a receipt, and putting
 * the difference last means the student never pays more up front than an even
 * split would ask.
 */
public final class ScheduleBuilder {

    private ScheduleBuilder() {
    }

    /**
     * @param firstDue the first installment's date; the rest follow at monthly intervals
     */
    public static List<FeeInstallment> evenSplit(BigDecimal netFee, int count, LocalDate firstDue) {
        if (count < 1) {
            throw new IllegalArgumentException("At least one installment is required");
        }
        BigDecimal each = netFee.divide(BigDecimal.valueOf(count), 0, RoundingMode.DOWN);
        BigDecimal last = netFee.subtract(each.multiply(BigDecimal.valueOf(count - 1)));

        List<FeeInstallment> schedule = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            schedule.add(FeeInstallment.builder()
                    .installmentNo(i)
                    .dueDate(firstDue.plusMonths(i - 1L))
                    .amount((i == count ? last : each).setScale(2, RoundingMode.HALF_UP))
                    .build());
        }
        return schedule;
    }

    public static BigDecimal total(List<FeeInstallment> schedule) {
        return schedule.stream().map(FeeInstallment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
