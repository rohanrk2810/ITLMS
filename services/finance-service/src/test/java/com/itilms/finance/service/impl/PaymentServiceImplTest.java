package com.itilms.finance.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.dto.request.RecordPaymentRequest;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.FeePlanStatus;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.PaymentMethod;
import com.itilms.finance.entity.PaymentStatus;
import com.itilms.finance.ledger.ScheduleBuilder;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;

/** The ways money can be recorded wrongly, and the one way it should be recorded. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private FeePlanRepository planRepository;
    @Mock private EventPublisher events;

    private PaymentServiceImpl service;
    private FeePlan plan;
    private final List<Payment> existing = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(paymentRepository, planRepository, new FinanceProperties(), events);

        AppPrincipal cashier = new AppPrincipal(7L, "cash@x", "Cashier", "FINANCE", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(cashier, null, cashier.authorities()));

        plan = FeePlan.builder().id(1L).studentId(3L).studentUserId(30L).courseId(1L).build();
        plan.setTerms(BigDecimal.valueOf(30_000), BigDecimal.ZERO);
        plan.replaceInstallments(new ArrayList<>(
                ScheduleBuilder.evenSplit(plan.getNetFee(), 3, LocalDate.now().minusMonths(1))));

        when(planRepository.lockById(1L)).thenReturn(Optional.of(plan));
        when(paymentRepository.findByFeePlanIdOrderByPaymentDateAscIdAsc(1L)).thenReturn(existing);
        when(paymentRepository.nextReceiptSequence()).thenReturn(42L);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static RecordPaymentRequest pay(long rupees, String method, String reference) {
        return new RecordPaymentRequest(1L, BigDecimal.valueOf(rupees), null, method, reference, null);
    }

    @Test
    @DisplayName("More than is outstanding is refused - no overpayment to refund later")
    void overpaymentRefused() {
        assertThatThrownBy(() -> service.record(pay(30_001, "CASH", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("more than");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("A UPI payment without its transaction id is refused")
    void referenceRequired() {
        assertThatThrownBy(() -> service.record(pay(5_000, "UPI", " ")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reference");
    }

    @Test
    @DisplayName("The same UPI transaction cannot be recorded twice")
    void duplicateReference() {
        when(paymentRepository.existsByMethodAndReferenceNoAndStatus(PaymentMethod.UPI, "UTR123", PaymentStatus.SUCCESS))
                .thenReturn(true);

        assertThatThrownBy(() -> service.record(pay(5_000, "UPI", "UTR123")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("No money is taken on a cancelled plan")
    void cancelledPlan() {
        plan.setStatus(FeePlanStatus.CANCELLED);
        assertThatThrownBy(() -> service.record(pay(1_000, "CASH", null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Paying the balance settles the plan and issues a numbered receipt")
    void settles() {
        existing.add(Payment.builder().feePlanId(1L).studentId(3L).amount(new BigDecimal("20000.00"))
                .paymentDate(LocalDate.now()).method(PaymentMethod.CASH).receiptNo("R1").build());

        var payment = service.record(pay(10_000, "CASH", null));

        assertThat(plan.getStatus()).isEqualTo(FeePlanStatus.SETTLED);
        assertThat(payment.receiptNo()).matches("RCPT-\\d{4}-000042");
        assertThat(payment.status()).isEqualTo("SUCCESS");
    }
}
