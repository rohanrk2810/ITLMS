package com.itilms.finance.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.Expense;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.repository.ExpenseRepository;
import com.itilms.finance.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/** What the institute spends, and the profit or loss of a period (fees collected less expenses). */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final PaymentRepository payments;
    private final FinanceProperties props;
    private final EventPublisher events;

    public record ExpenseRequest(LocalDate date, Expense.Category category, BigDecimal amount, String description,
                                 String paidTo, String referenceNo) {
    }

    public record ExpenseView(Long id, LocalDate date, String category, BigDecimal amount, String description,
                              String paidTo, String referenceNo, boolean voided, String voidReason) {
        static ExpenseView from(Expense e) {
            return new ExpenseView(e.getId(), e.getExpenseDate(), e.getCategory().name(), e.getAmount(),
                    e.getDescription(), e.getPaidTo(), e.getReferenceNo(), !e.counts(), e.getVoidReason());
        }
    }

    public record Summary(LocalDate from, LocalDate to, BigDecimal collected, BigDecimal spent, BigDecimal profit,
                          Map<String, BigDecimal> byCategory) {
    }

    @Transactional
    public ExpenseView add(ExpenseRequest request) {
        LocalDate today = LocalDate.now(props.getZone());
        LocalDate date = request.date() != null ? request.date() : today;
        if (date.isAfter(today)) {
            throw new BusinessRuleException("An expense cannot be dated in the future.");
        }
        Expense saved = expenses.save(Expense.builder().expenseDate(date).category(request.category())
                .amount(FeePlan.money(request.amount())).description(request.description().trim())
                .paidTo(blankToNull(request.paidTo())).referenceNo(blankToNull(request.referenceNo())).build());
        events.audit("finance-service", "EXPENSE_RECORDED", "Expense", saved.getId(), null,
                Map.of("category", saved.getCategory().name(), "amount", saved.getAmount().toPlainString()));
        return ExpenseView.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ExpenseView> list(LocalDate from, LocalDate to) {
        return expenses.findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(from, to).stream()
                .map(ExpenseView::from).toList();
    }

    @Transactional
    public ExpenseView voidExpense(Long id, String reason) {
        Expense expense = expenses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Expense", id));
        if (!expense.counts()) {
            throw new BusinessRuleException("This expense has already been voided.");
        }
        expense.setVoidedAt(Instant.now());
        expense.setVoidReason(reason.trim());
        expenses.save(expense);
        events.audit("finance-service", "EXPENSE_VOIDED", "Expense", id, null, Map.of("reason", reason.trim()));
        return ExpenseView.from(expense);
    }

    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to) {
        return summarise(from, to, payments.collectedBetween(from, to),
                expenses.findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(from, to));
    }

    /** Pure, so the arithmetic can be tested: voided expenses are left out. */
    static Summary summarise(LocalDate from, LocalDate to, BigDecimal collected, List<Expense> rows) {
        Map<Expense.Category, BigDecimal> byCategory = new EnumMap<>(Expense.Category.class);
        BigDecimal spent = BigDecimal.ZERO;
        for (Expense e : rows) {
            if (e.counts()) {
                spent = spent.add(e.getAmount());
                byCategory.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
            }
        }
        Map<String, BigDecimal> named = new java.util.LinkedHashMap<>();
        byCategory.forEach((k, v) -> named.put(k.name(), v));
        return new Summary(from, to, collected, spent, collected.subtract(spent), named);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
