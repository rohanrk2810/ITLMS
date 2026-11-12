package com.itilms.finance.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.itilms.finance.entity.Expense;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.FeePlanStatus;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.PaymentStatus;
import com.itilms.finance.entity.Refund;

/**
 * The institute's books in double entry, worked out from the records that already exist: fee plans, payments, refunds
 * and expenses. Nothing is posted twice and nothing can drift: a payment reversed, an expense voided or a refund paid
 * changes the books because it changed the record.
 *
 * <p>Every entry is balanced by construction (debits equal credits). Pure, so it can be tested without a database.
 *
 * <p>What it is and is not: fees are booked as income when the plan is raised and as money in when it is received;
 * extra charges, late fees and a changed discount are booked on the plan's creation date, because the plan keeps no
 * history of when they were added. Salary is an expense when it is paid, not when it is earned.
 */
public final class BooksBuilder {

    private BooksBuilder() {
    }

    public enum Type { ASSET, LIABILITY, INCOME, EXPENSE }

    public record Account(String code, String name, Type type) {
    }

    public record Line(String account, BigDecimal debit, BigDecimal credit) {
    }

    public record Entry(LocalDate date, String reference, String description, List<Line> lines) {
    }

    public static final String CASH = "1000";
    public static final String BANK = "1010";
    public static final String UPI = "1020";
    public static final String UNSOURCED = "1095";
    public static final String RECEIVABLE = "1100";
    public static final String GST_PAYABLE = "2000";
    public static final String REFUNDS_PAYABLE = "2100";
    public static final String FEE_INCOME = "4000";
    public static final String DISCOUNTS = "4100";

    public static final List<Account> ACCOUNTS = List.of(
            new Account(CASH, "Cash in hand", Type.ASSET),
            new Account(BANK, "Bank (transfer, cheque)", Type.ASSET),
            new Account(UPI, "UPI, card and online", Type.ASSET),
            new Account(UNSOURCED, "Cash or bank, source not recorded", Type.ASSET),
            new Account(RECEIVABLE, "Fees receivable", Type.ASSET),
            new Account(GST_PAYABLE, "GST payable", Type.LIABILITY),
            new Account(REFUNDS_PAYABLE, "Refunds payable", Type.LIABILITY),
            new Account(FEE_INCOME, "Fee income", Type.INCOME),
            new Account(DISCOUNTS, "Discounts allowed", Type.INCOME),
            new Account("5000", "Salaries", Type.EXPENSE),
            new Account("5010", "Rent", Type.EXPENSE),
            new Account("5020", "Utilities", Type.EXPENSE),
            new Account("5030", "Marketing", Type.EXPENSE),
            new Account("5040", "Supplies", Type.EXPENSE),
            new Account("5050", "Maintenance", Type.EXPENSE),
            new Account("5090", "Other expenses", Type.EXPENSE));

    public static String expenseAccount(Expense.Category c) {
        return switch (c) {
            case SALARY -> "5000";
            case RENT -> "5010";
            case UTILITIES -> "5020";
            case MARKETING -> "5030";
            case SUPPLIES -> "5040";
            case MAINTENANCE -> "5050";
            case OTHER -> "5090";
        };
    }

    /** Where money in or out of a method goes: cash, the bank, or UPI and online. */
    public static String moneyAccount(String method) {
        if (method == null) {
            return UNSOURCED;
        }
        return switch (method) {
            case "CASH" -> CASH;
            case "BANK_TRANSFER", "CHEQUE" -> BANK;
            case "UPI", "CARD", "ONLINE" -> UPI;
            default -> UNSOURCED;
        };
    }

    public static List<Entry> build(List<FeePlan> plans, List<Payment> payments, List<Refund> refunds,
                                    List<Expense> expenses, Map<Long, String> salaryMethodByExpenseId,
                                    BigDecimal gstRate, ZoneId zone) {
        return build(plans, payments, refunds, expenses, salaryMethodByExpenseId, date -> gstRate, zone);
    }

    /** The GST rate may depend on the date a fee was billed: GST applies only from its effective date. */
    public static List<Entry> build(List<FeePlan> plans, List<Payment> payments, List<Refund> refunds,
                                    List<Expense> expenses, Map<Long, String> salaryMethodByExpenseId,
                                    java.util.function.Function<LocalDate, BigDecimal> gstRateOn, ZoneId zone) {
        return build(plans, payments, refunds, expenses, salaryMethodByExpenseId, gstRateOn, zone, List.of());
    }

    /** As above, with the GST paid to the government. */
    public static List<Entry> build(List<FeePlan> plans, List<Payment> payments, List<Refund> refunds,
                                    List<Expense> expenses, Map<Long, String> salaryMethodByExpenseId,
                                    java.util.function.Function<LocalDate, BigDecimal> gstRateOn, ZoneId zone,
                                    List<com.itilms.finance.entity.GstPayment> gstPayments) {
        List<Entry> out = new ArrayList<>();

        for (FeePlan plan : plans) {
            LocalDate raised = plan.getCreatedAt() == null ? LocalDate.now(zone) : plan.getCreatedAt().atZone(zone).toLocalDate();
            String ref = "PLAN-" + plan.getId();
            String who = (plan.getStudentName() == null ? "student " + plan.getStudentId() : plan.getStudentName());
            out.add(billed(raised, ref, "Fee billed: " + who, plan.getTotalFee(), plan.getDiscount(), gstRateOn.apply(raised), false));
            if (plan.getStatus() == FeePlanStatus.CANCELLED && plan.getCancelledAt() != null) {
                // Undone with the rate it was billed at, so the tax comes back out exactly as it went in.
                out.add(billed(plan.getCancelledAt().atZone(zone).toLocalDate(), ref, "Fee plan cancelled: " + who,
                        plan.getTotalFee(), plan.getDiscount(), gstRateOn.apply(raised), true));
            }
        }

        for (Payment p : payments) {
            String account = moneyAccount(p.getMethod().name());
            out.add(entry(p.getPaymentDate(), p.getReceiptNo(), "Fee received (" + p.getMethod() + ")",
                    dr(account, p.getAmount()), cr(RECEIVABLE, p.getAmount())));
            if (p.getStatus() == PaymentStatus.REVERSED && p.getReversedAt() != null) {
                out.add(entry(p.getReversedAt().atZone(zone).toLocalDate(), p.getReceiptNo(), "Payment reversed",
                        dr(RECEIVABLE, p.getAmount()), cr(account, p.getAmount())));
            }
        }

        for (Refund r : refunds) {
            if (r.getStatus() == Refund.Status.APPROVED || r.getStatus() == Refund.Status.PAID) {
                LocalDate decided = r.getDecidedAt() == null ? LocalDate.now(zone) : r.getDecidedAt().atZone(zone).toLocalDate();
                out.add(entry(decided, "REFUND-" + r.getId(), "Refund approved",
                        dr(RECEIVABLE, r.getAmount()), cr(REFUNDS_PAYABLE, r.getAmount())));
            }
            if (r.getStatus() == Refund.Status.PAID && r.getPaidOn() != null) {
                out.add(entry(r.getPaidOn(), "REFUND-" + r.getId(), "Refund paid out (" + r.getPayMethod() + ")",
                        dr(REFUNDS_PAYABLE, r.getAmount()), cr(moneyAccount(r.getPayMethod()), r.getAmount())));
            }
        }

        for (Expense e : expenses) {
            String source = e.getId() != null && salaryMethodByExpenseId.containsKey(e.getId())
                    ? moneyAccount(salaryMethodByExpenseId.get(e.getId())) : UNSOURCED;
            String ref = "EXP-" + e.getId();
            BigDecimal gst = e.getGstAmount() == null ? BigDecimal.ZERO : e.getGstAmount();
            BigDecimal cost = e.getAmount().subtract(gst);
            // The GST in an expense is a credit against the GST owed, so it goes to the GST account, not to the cost.
            List<Line> spend = new ArrayList<>();
            spend.add(dr(expenseAccount(e.getCategory()), cost));
            if (gst.signum() > 0) {
                spend.add(dr(GST_PAYABLE, gst));
            }
            spend.add(cr(source, e.getAmount()));
            out.add(new Entry(e.getExpenseDate(), ref, e.getCategory() + ": " + e.getDescription(), spend));
            if (!e.counts() && e.getVoidedAt() != null) {
                List<Line> undo = new ArrayList<>();
                undo.add(dr(source, e.getAmount()));
                undo.add(cr(expenseAccount(e.getCategory()), cost));
                if (gst.signum() > 0) {
                    undo.add(cr(GST_PAYABLE, gst));
                }
                out.add(new Entry(e.getVoidedAt().atZone(zone).toLocalDate(), ref, "Expense voided", undo));
            }
        }

        for (com.itilms.finance.entity.GstPayment g : gstPayments) {
            out.add(entry(g.getPaidOn(), "GST-" + g.getId(), "GST paid to the government",
                    dr(GST_PAYABLE, g.getAmount()), cr(moneyAccount(g.getMethod()), g.getAmount())));
        }

        out.sort(Comparator.comparing(Entry::date).thenComparing(Entry::reference));
        return out;
    }

    /** A fee plan raised: receivable and income (net of the tax in it), the discount taken off, or all of it undone. */
    private static Entry billed(LocalDate date, String ref, String description, BigDecimal total, BigDecimal discount,
                                BigDecimal gstRate, boolean reverse) {
        GstMath.Split fee = GstMath.split(total, gstRate);
        GstMath.Split off = GstMath.split(discount, gstRate);
        List<Line> lines = new ArrayList<>();
        add(lines, RECEIVABLE, total, null);
        add(lines, FEE_INCOME, null, fee.taxable());
        add(lines, GST_PAYABLE, null, fee.tax());
        if (discount.signum() > 0) {
            add(lines, DISCOUNTS, off.taxable(), null);
            add(lines, GST_PAYABLE, off.tax(), null);
            add(lines, RECEIVABLE, null, discount);
        }
        if (reverse) {
            lines = lines.stream().map(l -> new Line(l.account(), l.credit(), l.debit())).toList();
        }
        return new Entry(date, ref, description, merge(lines));
    }

    private static void add(List<Line> lines, String account, BigDecimal debit, BigDecimal credit) {
        BigDecimal d = debit == null ? BigDecimal.ZERO : debit;
        BigDecimal c = credit == null ? BigDecimal.ZERO : credit;
        if (d.signum() != 0 || c.signum() != 0) {
            lines.add(new Line(account, d, c));
        }
    }

    /** One line per account in an entry (the receivable is both debited and credited when a discount is given). */
    private static List<Line> merge(List<Line> lines) {
        Map<String, BigDecimal[]> byAccount = new java.util.LinkedHashMap<>();
        for (Line l : lines) {
            BigDecimal[] sums = byAccount.computeIfAbsent(l.account(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            sums[0] = sums[0].add(l.debit());
            sums[1] = sums[1].add(l.credit());
        }
        List<Line> merged = new ArrayList<>();
        byAccount.forEach((account, s) -> {
            BigDecimal net = s[0].subtract(s[1]);
            if (net.signum() > 0) {
                merged.add(new Line(account, net, BigDecimal.ZERO));
            } else if (net.signum() < 0) {
                merged.add(new Line(account, BigDecimal.ZERO, net.negate()));
            }
        });
        return merged;
    }

    private static Line dr(String account, BigDecimal amount) {
        return new Line(account, amount, BigDecimal.ZERO);
    }

    private static Line cr(String account, BigDecimal amount) {
        return new Line(account, BigDecimal.ZERO, amount);
    }

    private static Entry entry(LocalDate date, String ref, String description, Line debit, Line credit) {
        return new Entry(date, ref, description, List.of(debit, credit));
    }
}
