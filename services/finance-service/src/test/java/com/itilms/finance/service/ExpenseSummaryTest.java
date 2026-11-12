package com.itilms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.itilms.finance.entity.Expense;

class ExpenseSummaryTest {

    private static Expense expense(Expense.Category category, String amount) {
        return Expense.builder().expenseDate(LocalDate.of(2026, 1, 5)).category(category)
                .amount(new BigDecimal(amount)).description("x").build();
    }

    @Test
    void profitIsFeesLessExpensesAndVoidedOnesDoNotCount() {
        Expense voided = expense(Expense.Category.OTHER, "999");
        voided.setVoidedAt(Instant.now());

        var summary = ExpenseService.summarise(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                new BigDecimal("50000"), List.of(expense(Expense.Category.SALARY, "30000"),
                        expense(Expense.Category.RENT, "8000"), expense(Expense.Category.SALARY, "2000"), voided));

        assertThat(summary.spent()).isEqualByComparingTo("40000");
        assertThat(summary.profit()).isEqualByComparingTo("10000");
        assertThat(summary.byCategory().get("SALARY")).isEqualByComparingTo("32000");
        assertThat(summary.byCategory()).doesNotContainKey("OTHER");
    }

    @Test
    void aLossIsNegative() {
        var summary = ExpenseService.summarise(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                new BigDecimal("1000"), List.of(expense(Expense.Category.RENT, "3000")));

        assertThat(summary.profit()).isEqualByComparingTo("-2000");
    }
}
