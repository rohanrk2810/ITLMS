package com.itilms.finance.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.NotificationRequestedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.dto.request.RefundRequest;
import com.itilms.finance.dto.response.RefundResponse;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.FeePlanStatus;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.Refund;
import com.itilms.finance.ledger.FeeLedger;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.repository.RefundRepository;

import lombok.RequiredArgsConstructor;

/**
 * Refunds: the finance desk asks, the administrator decides.
 *
 * <p>Approving records the amount on the payment ({@code refundedAmount}); what the student has paid is the payment
 * minus that, so the fee plan owes the money again and the reports follow. The money itself is handed back outside the
 * system, as with every other payment here.
 */
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final String SERVICE_NAME = "finance-service";

    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final FeePlanRepository plans;
    private final FinanceProperties props;
    private final EventPublisher events;

    @Transactional
    public RefundResponse request(RefundRequest request) {
        Payment payment = payments.findById(request.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", request.paymentId()));
        if (!payment.counts()) {
            throw new BusinessRuleException("This payment was reversed; there is nothing to refund on it.");
        }
        BigDecimal amount = FeePlan.money(request.amount());
        BigDecimal available = payment.netAmount().subtract(refunds.pendingForPayment(payment.getId()));
        if (amount.compareTo(available) > 0) {
            throw new BusinessRuleException("At most %s %s can still be refunded on this payment."
                    .formatted(props.getCurrencySymbol(), available.max(BigDecimal.ZERO).toPlainString()));
        }
        Refund refund = refunds.save(Refund.builder().paymentId(payment.getId()).feePlanId(payment.getFeePlanId())
                .studentId(payment.getStudentId()).amount(amount).reason(request.reason().trim()).build());
        events.notifyRole(com.itilms.common.security.Roles.ADMIN, "REFUND", "Refund to approve",
                "%s %s on receipt %s: %s".formatted(props.getCurrencySymbol(), amount.toPlainString(),
                        payment.getReceiptNo(), refund.getReason()), "/finance");
        events.audit(SERVICE_NAME, "REFUND_REQUESTED", "Refund", refund.getId(), null,
                Map.of("paymentId", payment.getId(), "amount", amount.toPlainString(), "reason", refund.getReason()));
        return view(refund);
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> list(Refund.Status status) {
        List<Refund> rows = status == null ? refunds.findAllByOrderByIdDesc() : refunds.findByStatusOrderByIdDesc(status);
        return rows.stream().map(this::view).toList();
    }

    @Transactional
    public RefundResponse approve(Long id, String note) {
        AppPrincipal admin = SecurityUtils.requirePrincipal();
        Refund refund = requireOpen(id);
        Payment payment = payments.lockById(refund.getPaymentId()).orElseThrow();
        if (!payment.counts() || refund.getAmount().compareTo(payment.netAmount()) > 0) {
            throw new BusinessRuleException("The payment no longer has this much left to refund.");
        }
        payment.setRefundedAmount(payment.getRefundedAmount().add(refund.getAmount()));
        payments.save(payment);

        FeePlan plan = plans.lockById(refund.getFeePlanId()).orElseThrow();
        FeeLedger ledger = FeeLedger.of(plan, payments.findByFeePlanIdOrderByPaymentDateAscIdAsc(plan.getId()),
                LocalDate.now(props.getZone()));
        if (plan.getStatus() != FeePlanStatus.CANCELLED) {
            plan.setStatus(ledger.isSettled() ? FeePlanStatus.SETTLED : FeePlanStatus.ACTIVE);
            plans.save(plan);
        }
        decide(refund, Refund.Status.APPROVED, admin, note);
        if (plan.getStudentUserId() != null) {
            events.publishAfterCommit(KafkaTopics.NOTIFICATION_REQUESTED, NotificationRequestedEvent.toUsersWithEmail(
                    List.of(plan.getStudentUserId()), "REFUND", "Refund approved",
                    "%s %s on receipt %s will be returned to you. Your balance is now %s %s."
                            .formatted(props.getCurrencySymbol(), refund.getAmount().toPlainString(),
                                    payment.getReceiptNo(), props.getCurrencySymbol(),
                                    ledger.outstanding().toPlainString()), "/student/fees"));
        }
        events.audit(SERVICE_NAME, "REFUND_APPROVED", "Refund", id, null,
                Map.of("paymentId", payment.getId(), "amount", refund.getAmount().toPlainString()));
        return view(refund);
    }

    @Transactional
    public RefundResponse reject(Long id, String note) {
        AppPrincipal admin = SecurityUtils.requirePrincipal();
        Refund refund = requireOpen(id);
        decide(refund, Refund.Status.REJECTED, admin, note);
        events.audit(SERVICE_NAME, "REFUND_REJECTED", "Refund", id, null, Map.of("note", note == null ? "" : note));
        return view(refund);
    }

    /** The money has been handed back: record how, when and by whom. Only an approved refund can be paid out. */
    @Transactional
    public RefundResponse payOut(Long id, String method, String reference, LocalDate paidOn) {
        AppPrincipal desk = SecurityUtils.requirePrincipal();
        Refund refund = refunds.findById(id).orElseThrow(() -> new ResourceNotFoundException("Refund", id));
        if (refund.getStatus() != Refund.Status.APPROVED) {
            throw new BusinessRuleException("Only an approved refund can be marked as paid.");
        }
        String how = method == null ? "" : method.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("CASH", "UPI", "BANK_TRANSFER", "CHEQUE").contains(how)) {
            throw new BusinessRuleException("Choose how it was paid: cash, UPI, bank transfer or cheque.");
        }
        LocalDate today = LocalDate.now(props.getZone());
        LocalDate day = paidOn != null ? paidOn : today;
        if (day.isAfter(today)) {
            throw new BusinessRuleException("A refund cannot be dated in the future.");
        }
        refund.setStatus(Refund.Status.PAID);
        refund.setPaidOn(day);
        refund.setPayMethod(how);
        refund.setPayReference(reference == null || reference.isBlank() ? null : reference.trim());
        refund.setPaidBy(desk.userId());
        refunds.save(refund);
        FeePlan plan = plans.findById(refund.getFeePlanId()).orElse(null);
        if (plan != null && plan.getStudentUserId() != null) {
            events.notifyUsers(List.of(plan.getStudentUserId()), "REFUND", "Refund paid",
                    "%s %s has been returned to you (%s).".formatted(props.getCurrencySymbol(),
                            refund.getAmount().toPlainString(), how.toLowerCase(java.util.Locale.ROOT).replace('_', ' ')),
                    "/student/fees");
        }
        events.audit(SERVICE_NAME, "REFUND_PAID_OUT", "Refund", id, null,
                Map.of("amount", refund.getAmount().toPlainString(), "method", how, "paidOn", day.toString()));
        return view(refund);
    }

    private Refund requireOpen(Long id) {
        Refund refund = refunds.findById(id).orElseThrow(() -> new ResourceNotFoundException("Refund", id));
        if (refund.getStatus() != Refund.Status.REQUESTED) {
            throw new BusinessRuleException("This refund has already been decided.");
        }
        return refund;
    }

    private void decide(Refund refund, Refund.Status status, AppPrincipal by, String note) {
        refund.setStatus(status);
        refund.setDecidedBy(by.userId());
        refund.setDecidedAt(Instant.now());
        refund.setDecisionNote(note == null || note.isBlank() ? null : note.trim());
        refunds.save(refund);
    }

    private RefundResponse view(Refund refund) {
        FeePlan plan = plans.findById(refund.getFeePlanId()).orElse(null);
        Payment payment = payments.findById(refund.getPaymentId()).orElse(null);
        return RefundResponse.from(refund, plan == null ? null : plan.getStudentName(),
                payment == null ? null : payment.getReceiptNo());
    }
}
