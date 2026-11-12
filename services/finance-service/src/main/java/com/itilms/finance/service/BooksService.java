package com.itilms.finance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.Payslip;
import com.itilms.finance.ledger.BooksBuilder;
import com.itilms.finance.ledger.BooksBuilder.Account;
import com.itilms.finance.ledger.BooksBuilder.Entry;
import com.itilms.finance.ledger.BooksBuilder.Type;
import com.itilms.finance.repository.ExpenseRepository;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.repository.PayslipRepository;
import com.itilms.finance.repository.RefundRepository;

import lombok.RequiredArgsConstructor;

/** The books for a period: journal, trial balance, profit and loss and balance sheet, all worked out from the records. */
@Service
@RequiredArgsConstructor
public class BooksService {

    private final FeePlanRepository plans;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final ExpenseRepository expenses;
    private final PayslipRepository slips;
    private final FinanceProperties props;
    private final GstSettingsService gstSettings;
    private final com.itilms.finance.repository.GstPaymentRepository gstPayments;

    public record Balance(String code, String name, String type, BigDecimal debit, BigDecimal credit) {
    }

    public record Statement(List<Balance> lines, BigDecimal total) {
    }

    public record ProfitLoss(List<Balance> income, List<Balance> expenses, BigDecimal totalIncome,
                             BigDecimal totalExpenses, BigDecimal surplus) {
    }

    public record BalanceSheet(LocalDate asOf, List<Balance> assets, List<Balance> liabilities, BigDecimal totalAssets,
                               BigDecimal totalLiabilities, BigDecimal surplus, boolean balances) {
    }

    public record Books(LocalDate from, LocalDate to, List<Account> accounts, List<Entry> journal,
                        List<Balance> trialBalance, ProfitLoss profitLoss, BalanceSheet balanceSheet, boolean balanced) {
    }

    @Transactional(readOnly = true)
    public Books books(LocalDate from, LocalDate to) {
        Map<Long, String> salaryMethods = slips.findAll().stream().filter(p -> p.getExpenseId() != null)
                .collect(Collectors.toMap(Payslip::getExpenseId, p -> p.getPayMethod() == null ? "" : p.getPayMethod(), (a, b) -> a));
        List<Entry> all = BooksBuilder.build(plans.findAll(), payments.findAll(), refunds.findAll(), expenses.findAll(),
                salaryMethods, rateByDate(), props.getZone(), gstPayments.findAllByOrderByPaidOnDescIdDesc());
        return compose(from, to, all);
    }

    private java.util.function.Function<LocalDate, BigDecimal> rateByDate() {
        GstSettingsService.Settings current = gstSettings.current();
        return date -> GstSettingsService.rateFor(current, date);
    }

    /** Pure, so the statements can be tested from a list of entries. */
    static Books compose(LocalDate from, LocalDate to, List<Entry> all) {
        List<Entry> upTo = all.stream().filter(e -> !e.date().isAfter(to)).toList();
        List<Entry> inPeriod = upTo.stream().filter(e -> !e.date().isBefore(from)).toList();

        List<Balance> trial = balances(upTo, null);
        List<Balance> period = balances(inPeriod, null);

        List<Balance> income = period.stream().filter(b -> b.type().equals(Type.INCOME.name()))
                .map(b -> asCredit(b)).filter(b -> b.credit().signum() != 0).toList();
        List<Balance> spent = period.stream().filter(b -> b.type().equals(Type.EXPENSE.name()))
                .map(b -> asDebit(b)).filter(b -> b.debit().signum() != 0).toList();
        BigDecimal totalIncome = income.stream().map(Balance::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpent = spent.stream().map(Balance::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        ProfitLoss pl = new ProfitLoss(income, spent, totalIncome, totalSpent, totalIncome.subtract(totalSpent));

        List<Balance> assets = trial.stream().filter(b -> b.type().equals(Type.ASSET.name())).map(BooksService::asDebit)
                .filter(b -> b.debit().signum() != 0).toList();
        List<Balance> liabilities = trial.stream().filter(b -> b.type().equals(Type.LIABILITY.name())).map(BooksService::asCredit)
                .filter(b -> b.credit().signum() != 0).toList();
        BigDecimal incomeToDate = trial.stream().filter(b -> b.type().equals(Type.INCOME.name()))
                .map(b -> b.credit().subtract(b.debit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spentToDate = trial.stream().filter(b -> b.type().equals(Type.EXPENSE.name()))
                .map(b -> b.debit().subtract(b.credit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal surplus = incomeToDate.subtract(spentToDate);
        BigDecimal totalAssets = assets.stream().map(Balance::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLiabilities = liabilities.stream().map(Balance::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BalanceSheet sheet = new BalanceSheet(to, assets, liabilities, totalAssets, totalLiabilities, surplus,
                totalAssets.compareTo(totalLiabilities.add(surplus)) == 0);

        BigDecimal debits = upTo.stream().flatMap(e -> e.lines().stream()).map(BooksBuilder.Line::debit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = upTo.stream().flatMap(e -> e.lines().stream()).map(BooksBuilder.Line::credit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Books(from, to, BooksBuilder.ACCOUNTS, inPeriod, trial, pl, sheet, debits.compareTo(credits) == 0);
    }

    /** Debit and credit totals per account, accounts with no movement left out. */
    private static List<Balance> balances(List<Entry> entries, Type only) {
        Map<String, BigDecimal[]> sums = new LinkedHashMap<>();
        for (Entry e : entries) {
            for (BooksBuilder.Line l : e.lines()) {
                BigDecimal[] s = sums.computeIfAbsent(l.account(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                s[0] = s[0].add(l.debit());
                s[1] = s[1].add(l.credit());
            }
        }
        List<Balance> out = new ArrayList<>();
        for (Account a : BooksBuilder.ACCOUNTS) {
            BigDecimal[] s = sums.get(a.code());
            if (s != null && (only == null || a.type() == only)) {
                out.add(new Balance(a.code(), a.name(), a.type().name(), s[0], s[1]));
            }
        }
        out.sort(Comparator.comparing(Balance::code));
        return out;
    }

    /** A balance shown on its natural side: debit-minus-credit as a debit, or the reverse as a credit. */
    private static Balance asDebit(Balance b) {
        return new Balance(b.code(), b.name(), b.type(), b.debit().subtract(b.credit()), BigDecimal.ZERO);
    }

    private static Balance asCredit(Balance b) {
        return new Balance(b.code(), b.name(), b.type(), BigDecimal.ZERO, b.credit().subtract(b.debit()));
    }
}
