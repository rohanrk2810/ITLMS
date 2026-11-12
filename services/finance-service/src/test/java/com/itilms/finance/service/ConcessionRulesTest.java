package com.itilms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.finance.client.AdmissionClient;
import com.itilms.finance.client.CourseClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.dto.request.FeePlanRequest;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.ledger.ScheduleBuilder;
import com.itilms.finance.repository.CourseFeeTemplateRepository;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.service.impl.FeePlanServiceImpl;

/** A discount needs a reason, and a large one needs the administrator. */
class ConcessionRulesTest {

    private final FeePlanRepository plans = mock(FeePlanRepository.class);
    private final FeePlanServiceImpl service = new FeePlanServiceImpl(plans, mock(PaymentRepository.class),
            mock(AdmissionClient.class), mock(CourseClient.class), new FinanceProperties(), mock(EventPublisher.class),
            mock(CourseFeeTemplateRepository.class));

    private FeePlan plan() {
        FeePlan plan = FeePlan.builder().studentId(3L).studentUserId(103L).courseId(9L).currency("INR").build();
        plan.setId(11L);
        plan.setTerms(new BigDecimal("30000.00"), BigDecimal.ZERO);
        plan.replaceInstallments(new ArrayList<>(ScheduleBuilder.evenSplit(plan.getNetFee(), 1,
                LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(5))));
        when(plans.findWithInstallmentsById(11L)).thenReturn(Optional.of(plan));
        when(plans.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return plan;
    }

    private static void signInAs(String role) {
        AppPrincipal who = new AppPrincipal(1L, "x@x", "X", role, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(who, null, who.authorities()));
    }

    private static FeePlanRequest discount(String amount, String reason) {
        return new FeePlanRequest(null, null, null, new BigDecimal("30000"), new BigDecimal(amount), null, null, null,
                null, reason);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aDiscountWithoutAReasonIsRefused() {
        plan();
        signInAs("FINANCE");

        assertThatThrownBy(() -> service.update(11L, discount("1000", " ")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("why");
    }

    @Test
    void aSmallDiscountWithAReasonIsGivenAndRemembered() {
        FeePlan plan = plan();
        signInAs("FINANCE");

        service.update(11L, discount("2000", "Sibling of a student"));

        assertThat(plan.getDiscount()).isEqualByComparingTo("2000");
        assertThat(plan.getDiscountReason()).isEqualTo("Sibling of a student");
        assertThat(plan.getDiscountSetBy()).isEqualTo(1L);
    }

    @Test
    void aLargeDiscountNeedsTheAdministrator() {
        plan();
        signInAs("FINANCE");

        assertThatThrownBy(() -> service.update(11L, discount("6000", "Merit scholarship")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("administrator");
    }

    @Test
    void theAdministratorCanGiveALargeDiscount() {
        FeePlan plan = plan();
        signInAs("ADMIN");

        service.update(11L, discount("6000", "Merit scholarship"));

        assertThat(plan.getNetFee()).isEqualByComparingTo("24000");
    }
}
