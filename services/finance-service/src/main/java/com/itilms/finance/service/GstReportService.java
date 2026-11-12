package com.itilms.finance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.finance.client.AdmissionClient;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.GstPayment;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.PaymentStatus;
import com.itilms.finance.ledger.FeeLedger;
import com.itilms.finance.ledger.GstReturn;
import com.itilms.finance.ledger.PaymentTax;
import com.itilms.finance.ledger.StateCodes;
import com.itilms.finance.repository.ExpenseRepository;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.GstPaymentRepository;
import com.itilms.finance.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * GST for the return: every invoice (payment) with its tax, month by month the tax charged, the tax paid on expenses
 * that is claimed back, what was paid to the government and what is still payable. Off while the rate is zero.
 */
@Service
@RequiredArgsConstructor
public class GstReportService {

    private static final java.util.Set<String> METHODS = java.util.Set.of("CASH", "UPI", "BANK_TRANSFER", "CHEQUE", "CARD");

    private final PaymentRepository payments;
    private final FeePlanRepository plans;
    private final ExpenseRepository expenses;
    private final GstPaymentRepository gstPayments;
    private final AdmissionClient admission;
    private final GstSettingsService settings;
    private final BooksLockService booksLock;
    private final EventPublisher events;

    public record Report(boolean enabled, BigDecimal ratePercent, String gstin, LocalDate from, LocalDate to,
                         GstReturn.Summary summary, List<GstReturn.Invoice> invoices, List<GstPayment> payments) {
    }

    public record PaymentRequest(LocalDate paidOn, String forMonth, BigDecimal amount, String method, String reference,
                                 String note) {
    }

    @Transactional(readOnly = true)
    public Report report(LocalDate from, LocalDate to) {
        GstSettingsService.Settings current = settings.current();
        boolean on = current.percent() != null && current.percent().signum() > 0;
        List<GstPayment> paid = gstPayments.findByPaidOnBetweenOrderByPaidOnDescIdDesc(from, to);
        if (!on && paid.isEmpty()) {
            return new Report(false, BigDecimal.ZERO, current.gstin(), from, to,
                    GstReturn.summarise(List.of(), List.of(), List.of()), List.of(), List.of());
        }
        List<GstReturn.Invoice> invoices = on ? invoices(from, to, current) : List.of();
        var summary = GstReturn.summarise(invoices,
                expenses.findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(from, to), paid);
        return new Report(on, current.percent(), current.gstin(), from, to, summary, invoices, paid);
    }

    /** One invoice per payment received in the period, with the tax in what remains after refunds. */
    private List<GstReturn.Invoice> invoices(LocalDate from, LocalDate to, GstSettingsService.Settings current) {
        List<Payment> rows = payments.findByPaymentDateBetweenAndStatus(from, to, PaymentStatus.SUCCESS);
        Map<Long, FeePlan> planById = plans.findAllById(rows.stream().map(Payment::getFeePlanId).distinct().toList())
                .stream().collect(Collectors.toMap(FeePlan::getId, p -> p));
        Map<Long, AdmissionClient.StudentSummary> students = students(rows);
        Map<Long, List<Payment>> byPlan = new java.util.HashMap<>();
        List<GstReturn.Invoice> out = new ArrayList<>();
        for (Payment p : rows) {
            FeePlan plan = planById.get(p.getFeePlanId());
            AdmissionClient.StudentSummary s = students.get(p.getStudentId());
            String state = s == null ? null : s.state();
            List<PaymentTax.Part> parts = List.of();
            if (plan != null) {
                List<Payment> all = byPlan.computeIfAbsent(plan.getId(),
                        id -> payments.findByFeePlanIdOrderByPaymentDateAscIdAsc(id));
                parts = FeeLedger.slicesOf(plan, all, p).stream()
                        .map(sl -> new PaymentTax.Part(sl.amount(), sl.installment().isGstExempt())).toList();
            }
            var tax = PaymentTax.compute(p.getAmount(), parts, GstSettingsService.rateFor(current, p.getPaymentDate()),
                    PaymentTax.interState(state, current.gstin())).scaled(p.netAmount(), p.getAmount());
            out.add(new GstReturn.Invoice(p.getReceiptNo(), p.getPaymentDate(), plan == null ? "" : plan.getStudentName(),
                    StateCodes.codeOf(state) == null ? "" : state, tax.rate(), tax.taxable(), tax.exempt(), tax.cgst(),
                    tax.sgst(), tax.igst(), p.netAmount()));
        }
        out.sort(java.util.Comparator.comparing(GstReturn.Invoice::date).thenComparing(GstReturn.Invoice::receiptNo));
        return out;
    }

    private Map<Long, AdmissionClient.StudentSummary> students(List<Payment> rows) {
        try {
            List<Long> ids = rows.stream().map(Payment::getStudentId).distinct().toList();
            if (ids.isEmpty()) {
                return Map.of();
            }
            return admission.lookup(ids).stream().collect(Collectors.toMap(AdmissionClient.StudentSummary::id, s -> s, (a, b) -> a));
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    @Transactional
    public GstPayment recordPayment(PaymentRequest request) {
        String method = request.method() == null ? "" : request.method().trim().toUpperCase(java.util.Locale.ROOT);
        if (!METHODS.contains(method)) {
            throw new BusinessRuleException("Choose how the GST was paid: bank transfer, UPI, cash, cheque or card.");
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new BusinessRuleException("The amount paid must be more than zero.");
        }
        if (request.forMonth() != null && !request.forMonth().isBlank()) {
            try {
                java.time.YearMonth.parse(request.forMonth());
            } catch (RuntimeException e) {
                throw new BusinessRuleException("The month must look like 2026-03.");
            }
        }
        LocalDate paidOn = request.paidOn() != null ? request.paidOn() : LocalDate.now();
        booksLock.requireOpen(paidOn, "a GST payment");
        GstPayment saved = gstPayments.save(GstPayment.builder().paidOn(paidOn)
                .forMonth(request.forMonth() == null || request.forMonth().isBlank() ? null : request.forMonth())
                .amount(FeePlan.money(request.amount())).method(method)
                .reference(request.reference() == null || request.reference().isBlank() ? null : request.reference().trim())
                .note(request.note() == null || request.note().isBlank() ? null : request.note().trim()).build());
        events.audit("finance-service", "GST_PAID", "GstPayment", saved.getId(), null,
                Map.of("amount", saved.getAmount().toPlainString(), "method", method, "month", String.valueOf(saved.getForMonth())));
        return saved;
    }
}
