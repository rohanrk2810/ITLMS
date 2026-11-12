package com.itilms.finance.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.itilms.finance.entity.Expense;
import com.itilms.finance.entity.GstPayment;

/**
 * The GST position, month by month: the tax charged on fees (output), the tax paid on expenses that can be claimed back
 * (input credit), what was paid to the government, and what is still payable. Pure, so it can be tested.
 */
public final class GstReturn {

    private GstReturn() {
    }

    /** One invoice (a payment, net of refunds): the receipt, the student and the tax in it. */
    public record Invoice(String receiptNo, LocalDate date, String student, String state, BigDecimal rate,
                          BigDecimal taxable, BigDecimal exempt, BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
                          BigDecimal total) {
    }

    public record Month(String month, int invoices, BigDecimal taxable, BigDecimal exempt, BigDecimal cgst,
                        BigDecimal sgst, BigDecimal igst, BigDecimal outputTax, BigDecimal inputCredit,
                        BigDecimal netPayable, BigDecimal paid) {
    }

    public record Summary(List<Month> months, BigDecimal outputTax, BigDecimal inputCredit, BigDecimal paid,
                          BigDecimal stillPayable) {
    }

    public static Summary summarise(List<Invoice> invoices, List<Expense> expenses, List<GstPayment> payments) {
        Map<YearMonth, BigDecimal[]> m = new TreeMap<>();
        for (Invoice i : invoices) {
            BigDecimal[] a = slot(m, YearMonth.from(i.date()));
            a[0] = a[0].add(i.taxable());
            a[1] = a[1].add(i.exempt());
            a[2] = a[2].add(i.cgst());
            a[3] = a[3].add(i.sgst());
            a[4] = a[4].add(i.igst());
            a[5] = a[5].add(BigDecimal.ONE);
        }
        for (Expense e : expenses) {
            if (e.counts() && e.getGstAmount().signum() > 0) {
                BigDecimal[] a = slot(m, YearMonth.from(e.getExpenseDate()));
                a[6] = a[6].add(e.getGstAmount());
            }
        }
        for (GstPayment p : payments) {
            BigDecimal[] a = slot(m, YearMonth.from(p.getPaidOn()));
            a[7] = a[7].add(p.getAmount());
        }
        List<Month> months = new ArrayList<>();
        BigDecimal output = BigDecimal.ZERO;
        BigDecimal input = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        for (Map.Entry<YearMonth, BigDecimal[]> e : m.entrySet()) {
            BigDecimal[] a = e.getValue();
            BigDecimal tax = a[2].add(a[3]).add(a[4]);
            months.add(new Month(e.getKey().toString(), a[5].intValue(), a[0], a[1], a[2], a[3], a[4], tax, a[6],
                    tax.subtract(a[6]), a[7]));
            output = output.add(tax);
            input = input.add(a[6]);
            paid = paid.add(a[7]);
        }
        return new Summary(months, output, input, paid, output.subtract(input).subtract(paid));
    }

    private static BigDecimal[] slot(Map<YearMonth, BigDecimal[]> m, YearMonth month) {
        return m.computeIfAbsent(month, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
    }
}
