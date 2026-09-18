package com.itilms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.StudentAdmittedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.finance.client.AdmissionClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.dto.request.FeePlanRequest;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.FeePlanStatus;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.service.impl.FeePlanServiceImpl;

class ReminderAndScheduleRulesTest {

    @Nested
    @DisplayName("Which reminder goes out (7, 3 and 1 days before)")
    class Reminders {

        private final List<Integer> steps = List.of(7, 3, 1);

        @Test
        @DisplayName("Nothing while the due date is more than a week away")
        void tooEarly() {
            assertThat(InstallmentReminderJob.reminderStep(10, null, steps)).isNull();
        }

        @Test
        @DisplayName("Each step is sent once")
        void onceEach() {
            assertThat(InstallmentReminderJob.reminderStep(7, null, steps)).isEqualTo(7);
            assertThat(InstallmentReminderJob.reminderStep(6, 7, steps)).isNull();
            assertThat(InstallmentReminderJob.reminderStep(3, 7, steps)).isEqualTo(3);
            assertThat(InstallmentReminderJob.reminderStep(1, 3, steps)).isEqualTo(1);
        }

        @Test
        @DisplayName("After missed mornings, only the reminder that fits now is sent")
        void catchUp() {
            // The scan did not run for a week; the student gets one message, not three.
            assertThat(InstallmentReminderJob.reminderStep(2, null, steps)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Raising a fee plan")
    class Plans {

        private final FeePlanRepository plans = mock(FeePlanRepository.class);
        private final AdmissionClient admission = mock(AdmissionClient.class);
        private final FeePlanServiceImpl service = new FeePlanServiceImpl(plans, mock(PaymentRepository.class),
                admission, new FinanceProperties(), mock(EventPublisher.class));

        @Test
        @DisplayName("An explicit schedule that does not add up to the net fee is refused")
        void scheduleMustAddUp() {
            AppPrincipal desk = new AppPrincipal(1L, "f@x", "F", "FINANCE", null);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(desk, null, desk.authorities()));
            when(admission.lookup(List.of(3L))).thenReturn(
                    List.of(new AdmissionClient.StudentSummary(3L, 30L, "STU-1", "Asha", "ACTIVE")));

            FeePlanRequest request = new FeePlanRequest(3L, 1L, null, BigDecimal.valueOf(30_000), null, null, null,
                    List.of(new FeePlanRequest.InstallmentInput(LocalDate.of(2026, 1, 1), BigDecimal.valueOf(10_000)),
                            new FeePlanRequest.InstallmentInput(LocalDate.of(2026, 2, 1), BigDecimal.valueOf(15_000))),
                    null);

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("add up to");
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("A full scholarship is settled at once, with nothing to fall due")
        void zeroFee() {
            when(plans.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
            var captured = org.mockito.ArgumentCaptor.forClass(FeePlan.class);

            service.createFromAdmission(new StudentAdmittedEvent("e", java.time.Instant.now(), 3L, 30L, "STU-1",
                    "Asha", "a@x", "9", 1L, 2L, LocalDate.of(2026, 1, 1), 1L,
                    BigDecimal.valueOf(40_000), BigDecimal.valueOf(40_000), 3));

            org.mockito.Mockito.verify(plans).save(captured.capture());
            assertThat(captured.getValue().getStatus()).isEqualTo(FeePlanStatus.SETTLED);
            assertThat(captured.getValue().getInstallments()).isEmpty();
        }

        @Test
        @DisplayName("A redelivered admission event does not raise a second plan")
        void idempotent() {
            when(plans.existsByStudentIdAndCourseIdAndStatusNot(3L, 1L, FeePlanStatus.CANCELLED)).thenReturn(true);

            service.createFromAdmission(new StudentAdmittedEvent("e", java.time.Instant.now(), 3L, 30L, "STU-1",
                    "Asha", "a@x", "9", 1L, 2L, LocalDate.of(2026, 1, 1), 1L,
                    BigDecimal.valueOf(40_000), null, 2));

            org.mockito.Mockito.verify(plans, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        }
    }
}
