package com.itilms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.dto.response.PaymentResponse;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.UpiClaim;
import com.itilms.finance.ledger.ScheduleBuilder;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.repository.UpiClaimRepository;

class UpiClaimServiceTest {

    private final UpiClaimRepository claims = mock(UpiClaimRepository.class);
    private final FeePlanRepository plans = mock(FeePlanRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final FinanceProperties props = new FinanceProperties();
    private final UpiClaimService service =
            new UpiClaimService(claims, plans, payments, paymentService, props, mock(EventPublisher.class));

    @BeforeEach
    void setUp() {
        props.setUpiId("institute@okbank");
        FeePlan plan = FeePlan.builder().studentId(7L).studentName("Asha").courseId(1L).currency("INR").build();
        plan.setId(11L);
        plan.setTerms(new BigDecimal("10000"), BigDecimal.ZERO);
        plan.replaceInstallments(new ArrayList<>(ScheduleBuilder.evenSplit(plan.getNetFee(), 1,
                LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(5))));
        when(plans.findWithInstallmentsById(11L)).thenReturn(Optional.of(plan));
        when(payments.findByFeePlanIdOrderByPaymentDateAscIdAsc(11L)).thenReturn(List.of());
        when(claims.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AppPrincipal student = new AppPrincipal(2L, "a@x", "Asha", "STUDENT", 7L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(student, null, student.authorities()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void isOffUntilTheInstituteSetsAUpiId() {
        props.setUpiId("");

        assertThat(service.config().enabled()).isFalse();
        assertThatThrownBy(() -> service.claim(new UpiClaimService.ClaimRequest(11L, new BigDecimal("1000"), "412345678901")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void takesAClaimForAnAmountOwed() {
        var view = service.claim(new UpiClaimService.ClaimRequest(11L, new BigDecimal("4000"), "412345678901"));

        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.amount()).isEqualByComparingTo("4000");
    }

    @Test
    void refusesMoreThanIsOwedAndABadReference() {
        assertThatThrownBy(() -> service.claim(new UpiClaimService.ClaimRequest(11L, new BigDecimal("10001"), "412345678901")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.claim(new UpiClaimService.ClaimRequest(11L, new BigDecimal("100"), "12")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("UTR");
    }

    @Test
    void refusesTheSameReferenceTwice() {
        when(claims.existsByUtrAndStatusNot("412345678901", UpiClaim.Status.REJECTED)).thenReturn(true);

        assertThatThrownBy(() -> service.claim(new UpiClaimService.ClaimRequest(11L, new BigDecimal("100"), "412345678901")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void confirmingRecordsAnOrdinaryUpiPayment() {
        UpiClaim claim = UpiClaim.builder().feePlanId(11L).studentId(7L).amount(new BigDecimal("4000"))
                .utr("412345678901").build();
        claim.setId(5L);
        when(claims.findById(5L)).thenReturn(Optional.of(claim));
        PaymentResponse recorded = new PaymentResponse(99L, 11L, 7L, new BigDecimal("4000"), LocalDate.now(), "UPI",
                "412345678901", "RCPT-1", "SUCCESS", null, null, null, null, BigDecimal.ZERO);
        when(paymentService.record(any())).thenReturn(recorded);

        var view = service.confirm(5L);

        assertThat(view.status()).isEqualTo("CONFIRMED");
        assertThat(view.paymentId()).isEqualTo(99L);
        verify(paymentService).record(any());
    }
}
