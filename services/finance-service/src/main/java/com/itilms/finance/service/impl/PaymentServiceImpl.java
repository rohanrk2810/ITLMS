package com.itilms.finance.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.PaymentRecordedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.dto.request.RecordPaymentRequest;
import com.itilms.finance.dto.response.PaymentResponse;
import com.itilms.finance.dto.response.ReceiptResponse;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.FeePlanStatus;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.PaymentMethod;
import com.itilms.finance.entity.PaymentStatus;
import com.itilms.finance.ledger.FeeLedger;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String SERVICE_NAME = "finance-service";

    private final PaymentRepository paymentRepository;
    private final FeePlanRepository planRepository;
    private final FinanceProperties props;
    private final EventPublisher events;

    @Override
    @Transactional
    public PaymentResponse record(RecordPaymentRequest request) {
        AppPrincipal cashier = SecurityUtils.requirePrincipal();
        FeePlan plan = planRepository.lockById(request.feePlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Fee plan", request.feePlanId()));
        if (plan.getStatus() == FeePlanStatus.CANCELLED) {
            throw new BusinessRuleException("This fee plan has been cancelled.");
        }

        PaymentMethod method = parseMethod(request.method());
        String reference = trim(request.referenceNo());
        if (method.needsReference() && reference == null) {
            throw new BusinessRuleException("A %s payment needs its transaction or reference number, "
                    .formatted(method) + "so it can be matched to the bank statement.");
        }
        // The database refuses a duplicate too; checking first gives the
        // cashier a sentence rather than a constraint name.
        if (reference != null
                && paymentRepository.existsByMethodAndReferenceNoAndStatus(method, reference, PaymentStatus.SUCCESS)) {
            throw new DuplicateResourceException(
                    "A %s payment with reference %s has already been recorded.".formatted(method, reference));
        }

        LocalDate today = today();
        List<Payment> payments = new ArrayList<>(
                paymentRepository.findByFeePlanIdOrderByPaymentDateAscIdAsc(plan.getId()));
        FeeLedger before = FeeLedger.of(plan, payments, today);
        if (FeePlan.money(request.amount()).compareTo(before.outstanding()) > 0) {
            throw new BusinessRuleException("%s %s is more than the %s %s outstanding on this plan."
                    .formatted(props.getCurrencySymbol(), request.amount().toPlainString(),
                            props.getCurrencySymbol(), before.outstanding().toPlainString()));
        }

        Payment payment = paymentRepository.save(Payment.builder()
                .feePlanId(plan.getId())
                .studentId(plan.getStudentId())
                .amount(FeePlan.money(request.amount()))
                .paymentDate(request.paymentDate() != null ? request.paymentDate() : today)
                .method(method)
                .referenceNo(reference)
                .receiptNo(nextReceiptNo())
                .notes(trim(request.notes()))
                .build());

        payments.add(payment);
        FeeLedger after = FeeLedger.of(plan, payments, today);
        plan.setStatus(after.isSettled() ? FeePlanStatus.SETTLED : FeePlanStatus.ACTIVE);
        planRepository.save(plan);

        events.publishAfterCommit(KafkaTopics.PAYMENT_RECORDED, String.valueOf(plan.getStudentId()),
                new PaymentRecordedEvent(DomainEvent.newId(), Instant.now(), payment.getId(), plan.getId(),
                        // Applied to the plan, not one installment: see FeeLedger.
                        null, plan.getStudentId(), plan.getStudentUserId(), payment.getAmount(),
                        after.outstanding(), method.name(), payment.getReceiptNo(), cashier.userId()));

        if (plan.getStudentUserId() != null) {
            events.notifyUsers(List.of(plan.getStudentUserId()), "PAYMENT",
                    "Payment received",
                    "%s %s received (receipt %s). %s".formatted(props.getCurrencySymbol(),
                            payment.getAmount().toPlainString(), payment.getReceiptNo(),
                            after.isSettled() ? "Your fees are fully paid."
                                    : "Balance: " + props.getCurrencySymbol() + " " + after.outstanding().toPlainString() + "."),
                    "/student/fees");
        }
        events.audit(SERVICE_NAME, "PAYMENT_RECORDED", "Payment", payment.getId(), null,
                Map.of("feePlanId", plan.getId(), "amount", payment.getAmount().toPlainString(),
                        "method", method.name(), "receiptNo", payment.getReceiptNo()));

        log.info("Recorded payment {} ({}) on plan {}", payment.getId(), payment.getReceiptNo(), plan.getId());
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional
    public PaymentResponse reverse(Long id, String reason) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        Payment payment = paymentRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        if (payment.getStatus() == PaymentStatus.REVERSED) {
            throw new BusinessRuleException("This payment has already been reversed.");
        }
        payment.reverse(reason.trim(), caller.userId(), Instant.now());
        paymentRepository.save(payment);

        // A settled plan with a bounced cheque is owed again.
        FeePlan plan = planRepository.lockById(payment.getFeePlanId()).orElseThrow();
        FeeLedger ledger = FeeLedger.of(plan,
                paymentRepository.findByFeePlanIdOrderByPaymentDateAscIdAsc(plan.getId()), today());
        if (plan.getStatus() != FeePlanStatus.CANCELLED) {
            plan.setStatus(ledger.isSettled() ? FeePlanStatus.SETTLED : FeePlanStatus.ACTIVE);
            planRepository.save(plan);
        }

        if (plan.getStudentUserId() != null) {
            events.notifyUsers(List.of(plan.getStudentUserId()), "PAYMENT",
                    "Payment reversed",
                    "The payment on receipt %s was reversed: %s. Balance: %s %s."
                            .formatted(payment.getReceiptNo(), reason.trim(), props.getCurrencySymbol(),
                                    ledger.outstanding().toPlainString()),
                    "/student/fees");
        }
        // Reversing money is the most sensitive thing this service does.
        events.audit(SERVICE_NAME, "PAYMENT_REVERSED", "Payment", id,
                Map.of("status", "SUCCESS", "amount", payment.getAmount().toPlainString()),
                Map.of("status", "REVERSED", "reason", reason.trim()));
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        Payment payment = requirePayment(id);
        SecurityUtils.requireStudentOwnershipOrStaff(payment.getStudentId());
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse receipt(Long id) {
        Payment payment = requirePayment(id);
        SecurityUtils.requireStudentOwnershipOrStaff(payment.getStudentId());
        FeePlan plan = planRepository.findWithInstallmentsById(payment.getFeePlanId()).orElseThrow();
        FeeLedger ledger = FeeLedger.of(plan,
                paymentRepository.findByFeePlanIdOrderByPaymentDateAscIdAsc(plan.getId()), today());

        return new ReceiptResponse(payment.getReceiptNo(), payment.getPaymentDate(),
                plan.getStudentCode(), plan.getStudentName(), plan.getCourseId(),
                plan.getCurrency(), props.getCurrencySymbol(), payment.getAmount(),
                payment.getMethod().name(), payment.getReferenceNo(), payment.getStatus().name(),
                plan.getNetFee(), ledger.paid(), ledger.outstanding());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(LocalDate from, LocalDate to, Pageable pageable) {
        LocalDate end = to != null ? to : today();
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        if (start.isAfter(end)) {
            throw new BusinessRuleException("The start date must not be after the end date.");
        }
        return PageResponse.from(
                paymentRepository.findByPaymentDateBetweenOrderByPaymentDateDescIdDesc(start, end, pageable),
                PaymentResponse::from);
    }

    // -----------------------------------------------------------------

    private String nextReceiptNo() {
        return "%s-%d-%06d".formatted(props.getReceiptPrefix(), Year.now(props.getZone()).getValue(),
                paymentRepository.nextReceiptSequence());
    }

    private PaymentMethod parseMethod(String value) {
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Payment method must be CASH, UPI, CARD, BANK_TRANSFER, CHEQUE or ONLINE.");
        }
    }

    private Payment requirePayment(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }

    private LocalDate today() {
        return LocalDate.now(props.getZone());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
