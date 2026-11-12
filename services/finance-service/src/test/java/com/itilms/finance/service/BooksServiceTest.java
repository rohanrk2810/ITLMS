package com.itilms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.itilms.finance.entity.Expense;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.PaymentMethod;
import com.itilms.finance.ledger.BooksBuilder;

class BooksServiceTest {

    @Test
    void theBalanceSheetBalancesAndProfitIsIncomeLessExpenses() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        FeePlan plan = FeePlan.builder().studentId(1L).studentName("Asha").courseId(1L).build();
        plan.setId(1L);
        plan.setTerms(new BigDecimal("20000"), new BigDecimal("2000"));
        plan.setCreatedAt(LocalDate.of(2026, 1, 5).atStartOfDay(zone).toInstant());
        Payment paid = Payment.builder().feePlanId(1L).studentId(1L).amount(new BigDecimal("8000")).method(PaymentMethod.CASH)
                .paymentDate(LocalDate.of(2026, 1, 20)).receiptNo("R1").build();
        paid.setId(1L);
        Expense rent = Expense.builder().expenseDate(LocalDate.of(2026, 1, 25)).category(Expense.Category.RENT)
                .amount(new BigDecimal("3000")).description("Rent").build();
        rent.setId(1L);

        var entries = BooksBuilder.build(List.of(plan), List.of(paid), List.of(), List.of(rent), Map.of(), new BigDecimal("18"), zone);
        var books = BooksService.compose(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), entries);

        assertThat(books.balanced()).isTrue();
        assertThat(books.balanceSheet().balances()).isTrue();
        // Fee income is the net-of-discount fee less its GST; expenses are the rent.
        assertThat(books.profitLoss().totalExpenses()).isEqualByComparingTo("3000");
        assertThat(books.profitLoss().surplus()).isEqualByComparingTo(
                books.profitLoss().totalIncome().subtract(new BigDecimal("3000")));
        // 10,000 still to collect (20,000 less 2,000 discount less 8,000 paid) is the receivable.
        assertThat(books.balanceSheet().assets().stream().filter(b -> b.code().equals(BooksBuilder.RECEIVABLE))
                .findFirst().orElseThrow().debit()).isEqualByComparingTo("10000");
    }

    @Test
    void aPeriodOnlyShowsItsOwnEntriesButTheTrialBalanceRunsFromTheStart() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        Expense january = Expense.builder().expenseDate(LocalDate.of(2026, 1, 10)).category(Expense.Category.RENT)
                .amount(new BigDecimal("1000")).description("Jan").build();
        january.setId(1L);
        Expense february = Expense.builder().expenseDate(LocalDate.of(2026, 2, 10)).category(Expense.Category.RENT)
                .amount(new BigDecimal("1500")).description("Feb").build();
        february.setId(2L);

        var entries = BooksBuilder.build(List.of(), List.of(), List.of(), List.of(january, february), Map.of(), BigDecimal.ZERO, zone);
        var books = BooksService.compose(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), entries);

        assertThat(books.journal()).hasSize(1);
        assertThat(books.profitLoss().totalExpenses()).isEqualByComparingTo("1500");
        assertThat(books.trialBalance().stream().filter(b -> b.code().equals("5010")).findFirst().orElseThrow().debit())
                .isEqualByComparingTo("2500");
    }
}
