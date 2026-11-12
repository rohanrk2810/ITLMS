package com.itilms.finance.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.Roles;
import com.itilms.common.security.SecurityUtils;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.dto.request.RecordPaymentRequest;
import com.itilms.finance.dto.response.PaymentResponse;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.FeePlanStatus;
import com.itilms.finance.entity.UpiClaim;
import com.itilms.finance.ledger.FeeLedger;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.repository.UpiClaimRepository;

import lombok.RequiredArgsConstructor;

/**
 * Paying by UPI outside the portal: the student pays the institute's UPI id from their own app and reports the
 * transaction reference; the finance desk matches it with the bank and confirms. Confirming records an ordinary UPI
 * payment (with its receipt and emailed PDF), so nothing here is a second way of counting money.
 */
@Service
@RequiredArgsConstructor
public class UpiClaimService {

    private static final String UTR_PATTERN = "^[A-Za-z0-9]{6,30}$";

    private final UpiClaimRepository claims;
    private final FeePlanRepository plans;
    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final FinanceProperties props;
    private final EventPublisher events;

    public record Config(boolean enabled, String upiId, String payeeName) {
    }

    public record ClaimView(Long id, Long feePlanId, Long studentId, String studentName, BigDecimal amount, String utr,
                            String status, Instant claimedAt, Instant decidedAt, String decisionNote, Long paymentId) {
    }

    public record ClaimRequest(Long feePlanId, BigDecimal amount, String utr) {
    }

    public Config config() {
        boolean on = props.getUpiId() != null && !props.getUpiId().isBlank();
        return new Config(on, on ? props.getUpiId().trim() : null, props.getUpiName() == null ? "" : props.getUpiName().trim());
    }

    @Transactional
    public ClaimView claim(ClaimRequest request) {
        if (!config().enabled()) {
            throw new BusinessRuleException("UPI payment is not set up yet. Please pay at the institute.");
        }
        AppPrincipal me = SecurityUtils.requirePrincipal();
        FeePlan plan = plans.findWithInstallmentsById(request.feePlanId())
                .filter(p -> p.getStudentId().equals(me.profileId()))
                .orElseThrow(() -> new ResourceNotFoundException("Fee plan", request.feePlanId()));
        if (plan.getStatus() != FeePlanStatus.ACTIVE) {
            throw new BusinessRuleException("There is nothing to pay on this fee plan.");
        }
        String utr = request.utr() == null ? "" : request.utr().trim();
        if (!utr.matches(UTR_PATTERN)) {
            throw new BusinessRuleException("Enter the UPI transaction reference (UTR): 6 to 30 letters and digits.");
        }
        BigDecimal amount = FeePlan.money(request.amount());
        BigDecimal outstanding = FeeLedger.of(plan, payments.findByFeePlanIdOrderByPaymentDateAscIdAsc(plan.getId()),
                LocalDate.now(props.getZone())).outstanding();
        if (amount.signum() <= 0 || amount.compareTo(outstanding) > 0) {
            throw new BusinessRuleException("The amount must be more than zero and not more than the %s %s still owed."
                    .formatted(props.getCurrencySymbol(), outstanding.toPlainString()));
        }
        if (claims.existsByUtrAndStatusNot(utr, UpiClaim.Status.REJECTED)) {
            throw new DuplicateResourceException("This transaction reference has already been sent.");
        }
        UpiClaim saved = claims.save(UpiClaim.builder().feePlanId(plan.getId()).studentId(plan.getStudentId())
                .amount(amount).utr(utr).build());
        events.notifyRole(Roles.FINANCE, "FEE_PLAN", "UPI payment to check",
                "%s sent %s %s, reference %s.".formatted(plan.getStudentName(), props.getCurrencySymbol(),
                        amount.toPlainString(), utr), "/finance");
        return view(saved, plan.getStudentName());
    }

    @Transactional(readOnly = true)
    public List<ClaimView> all() {
        return claims.findAllByOrderByIdDesc().stream().map(c -> view(c, nameOf(c))).toList();
    }

    @Transactional(readOnly = true)
    public List<ClaimView> mine() {
        Long studentId = SecurityUtils.requirePrincipal().profileId();
        return claims.findByStudentIdOrderByIdDesc(studentId).stream().map(c -> view(c, null)).toList();
    }

    /** Records the payment (so it gets a receipt like any other) and marks the claim confirmed. */
    @Transactional
    public ClaimView confirm(Long id) {
        AppPrincipal desk = SecurityUtils.requirePrincipal();
        UpiClaim claim = requirePending(id);
        PaymentResponse payment = paymentService.record(new RecordPaymentRequest(claim.getFeePlanId(), claim.getAmount(),
                null, "UPI", claim.getUtr(), "Confirmed from the student's UPI claim " + claim.getId()));
        claim.setStatus(UpiClaim.Status.CONFIRMED);
        claim.setPaymentId(payment.id());
        decide(claim, desk, null);
        events.audit("finance-service", "UPI_CLAIM_CONFIRMED", "UpiClaim", id, null,
                Map.of("paymentId", payment.id(), "utr", claim.getUtr()));
        return view(claim, nameOf(claim));
    }

    @Transactional
    public ClaimView reject(Long id, String reason) {
        AppPrincipal desk = SecurityUtils.requirePrincipal();
        UpiClaim claim = requirePending(id);
        claim.setStatus(UpiClaim.Status.REJECTED);
        decide(claim, desk, reason.trim());
        FeePlan plan = plans.findById(claim.getFeePlanId()).orElse(null);
        if (plan != null && plan.getStudentUserId() != null) {
            events.notifyUsers(List.of(plan.getStudentUserId()), "FEE_PLAN", "UPI payment not found",
                    "We could not match reference %s: %s. Please contact the institute office."
                            .formatted(claim.getUtr(), reason.trim()), "/student/fees");
        }
        events.audit("finance-service", "UPI_CLAIM_REJECTED", "UpiClaim", id, null, Map.of("reason", reason.trim()));
        return view(claim, nameOf(claim));
    }

    private UpiClaim requirePending(Long id) {
        UpiClaim claim = claims.findById(id).orElseThrow(() -> new ResourceNotFoundException("UPI claim", id));
        if (claim.getStatus() != UpiClaim.Status.PENDING) {
            throw new BusinessRuleException("This claim has already been decided.");
        }
        return claim;
    }

    private void decide(UpiClaim claim, AppPrincipal by, String note) {
        claim.setDecidedBy(by.userId());
        claim.setDecidedAt(Instant.now());
        claim.setDecisionNote(note);
        claims.save(claim);
    }

    private String nameOf(UpiClaim c) {
        return plans.findById(c.getFeePlanId()).map(FeePlan::getStudentName).orElse(null);
    }

    private ClaimView view(UpiClaim c, String studentName) {
        return new ClaimView(c.getId(), c.getFeePlanId(), c.getStudentId(), studentName, c.getAmount(), c.getUtr(),
                c.getStatus().name(), c.getCreatedAt(), c.getDecidedAt(), c.getDecisionNote(), c.getPaymentId());
    }
}
