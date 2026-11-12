package com.itilms.finance.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.itilms.finance.entity.Expense;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.PaymentMethod;
import com.itilms.finance.entity.PaymentStatus;
import com.itilms.finance.entity.Refund;

class BooksBuilderTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    private static FeePlan plan(String total, String discount) {
        FeePlan p = FeePlan.builder().studentId(1L).studentName("Asha").courseId(1L).build();
        p.setId(1L);
        p.setTerms(n(total), n(discount));
        p.setCreatedAt(LocalDate.of(2026, 1, 5).atStartOfDay(ZONE).toInstant());
        return p;
    }

    private static Payment payment(long id, String amount, PaymentMethod method, String date) {
        Payment p = Payment.builder().feePlanId(1L).studentId(1L).amount(n(amount)).method(method)
                .paymentDate(LocalDate.parse(date)).receiptNo("RCPT-" + id).build();
        p.setId(id);
        return p;
    }

    private static Map<String, BigDecimal> net(List<BooksBuilder.Entry> entries) {
        Map<String, BigDecimal> m = new java.util.TreeMap<>();
        entries.forEach(e -> e.lines().forEach(l -> m.merge(l.account(), l.debit().subtract(l.credit()), BigDecimal::add)));
        return m;
    }

    @Test
    void everyEntryBalances() {
        Payment reversed = payment(2, "1000", PaymentMethod.UPI, "2026-01-12");
        reversed.setStatus(PaymentStatus.REVERSED);
        reversed.setReversedAt(Instant.parse("2026-01-13T05:00:00Z"));
        Expense voided = Expense.builder().expenseDate(LocalDate.of(2026, 1, 8)).category(Expense.Category.RENT)
                .amount(n("500")).description("x").voidedAt(Instant.parse("2026-01-09T05:00:00Z")).build();
        voided.setId(9L);

        var entries = BooksBuilder.build(List.of(plan("11800", "1180")), List.of(payment(1, "5000", PaymentMethod.CASH, "2026-01-10"), reversed),
                List.<Refund>of(), List.of(voided), Map.of(), n("18"), ZONE);

        for (var e : entries) {
            BigDecimal d = e.lines().stream().map(BooksBuilder.Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal c = e.lines().stream().map(BooksBuilder.Line::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(d).as(e.description()).isEqualByComparingTo(c);
        }
    }

    @Test
    void feesPaymentsAndTheReceivableAgree() {
        var entries = BooksBuilder.build(List.of(plan("10000", "0")), List.of(payment(1, "4000", PaymentMethod.CASH, "2026-01-10"),
                payment(2, "1000", PaymentMethod.BANK_TRANSFER, "2026-01-11")), List.of(), List.of(), Map.of(), BigDecimal.ZERO, ZONE);

        var balances = net(entries);

        assertThat(balances.get(BooksBuilder.RECEIVABLE)).isEqualByComparingTo("5000");
        assertThat(balances.get(BooksBuilder.CASH)).isEqualByComparingTo("4000");
        assertThat(balances.get(BooksBuilder.BANK)).isEqualByComparingTo("1000");
        assertThat(balances.get(BooksBuilder.FEE_INCOME)).isEqualByComparingTo("-10000");
    }

    @Test
    void gstIsSplitOutOfTheFeeIncome() {
        var balances = net(BooksBuilder.build(List.of(plan("11800", "0")), List.of(), List.of(), List.of(), Map.of(), n("18"), ZONE));

        assertThat(balances.get(BooksBuilder.FEE_INCOME)).isEqualByComparingTo("-10000");
        assertThat(balances.get(BooksBuilder.GST_PAYABLE)).isEqualByComparingTo("-1800");
    }

    @Test
    void aReversedPaymentAndAVoidedExpenseNetToNothing() {
        Payment p = payment(1, "3000", PaymentMethod.CASH, "2026-01-10");
        p.setStatus(PaymentStatus.REVERSED);
        p.setReversedAt(Instant.parse("2026-01-11T05:00:00Z"));
        Expense e = Expense.builder().expenseDate(LocalDate.of(2026, 1, 8)).category(Expense.Category.SUPPLIES)
                .amount(n("800")).description("chalk").voidedAt(Instant.parse("2026-01-09T05:00:00Z")).build();
        e.setId(3L);

        var balances = net(BooksBuilder.build(new ArrayList<>(), List.of(p), List.of(), List.of(e), Map.of(), BigDecimal.ZERO, ZONE));

        assertThat(balances.get(BooksBuilder.CASH)).isEqualByComparingTo("0");
        assertThat(balances.get("5040")).isEqualByComparingTo("0");
    }

    @Test
    void aSalaryPaidByBankComesOutOfTheBank() {
        Expense salary = Expense.builder().expenseDate(LocalDate.of(2026, 2, 1)).category(Expense.Category.SALARY)
                .amount(n("30000")).description("Salary").build();
        salary.setId(5L);

        var balances = net(BooksBuilder.build(List.of(), List.of(), List.of(), List.of(salary), Map.of(5L, "BANK_TRANSFER"),
                BigDecimal.ZERO, ZONE));

        assertThat(balances.get(BooksBuilder.BANK)).isEqualByComparingTo("-30000");
        assertThat(balances.get("5000")).isEqualByComparingTo("30000");
    }
}
