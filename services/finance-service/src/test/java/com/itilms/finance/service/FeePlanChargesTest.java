package com.itilms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.StudentAdmittedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.finance.client.AdmissionClient;
import com.itilms.finance.client.CourseClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.dto.request.ChargeDtos.BatchChargeRequest;
import com.itilms.finance.dto.request.ChargeDtos.ChargeRequest;
import com.itilms.finance.dto.request.FeePlanRequest;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.FeePlanStatus;
import com.itilms.finance.entity.PaymentStatus;
import com.itilms.finance.ledger.ScheduleBuilder;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.service.impl.FeePlanServiceImpl;

/**
 * What a student owes must show up the moment they are placed in a batch, and the finance desk must be able to add a
 * development fee or a book on top of it, for one student or a whole batch, without disturbing what was agreed.
 */
class FeePlanChargesTest {

    private final FeePlanRepository plans = mock(FeePlanRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final CourseClient courses = mock(CourseClient.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final FinanceProperties props = new FinanceProperties();
    private final FeePlanServiceImpl service =
            new FeePlanServiceImpl(plans, payments, mock(AdmissionClient.class), courses, props, events,
                    mock(com.itilms.finance.repository.CourseFeeTemplateRepository.class));

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Kolkata"));
    }

    private static EnrollmentCreatedEvent placed(Long batchId) {
        return new EnrollmentCreatedEvent("ev", Instant.now(), 5L, 30L, 7L, 9L, batchId, "B-1", "STU-30", "Asha Patil");
    }

    private void courseFee(BigDecimal fee) {
        when(courses.publicDetail(9L)).thenReturn(new CourseClient.CourseDetail(
                new CourseClient.Course(9L, "Java", "PUBLISHED", fee)));
    }

    private FeePlan plan(long id, long student, Long batch) {
        FeePlan plan = FeePlan.builder().studentId(student).studentUserId(student + 100).courseId(9L).batchId(batch)
                .currency("INR").build();
        plan.setId(id);
        plan.setTerms(new BigDecimal("30000.00"), BigDecimal.ZERO);
        plan.replaceInstallments(new ArrayList<>(ScheduleBuilder.evenSplit(plan.getNetFee(), 3, today())));
        return plan;
    }

    // -----------------------------------------------------------------
    // Raised when the student is placed
    // -----------------------------------------------------------------

    @Test
    void placingAStudentRaisesTheCourseFeeAsOneInstallmentDueInAWeek() {
        courseFee(new BigDecimal("25000"));
        when(plans.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createFromEnrollment(placed(3L));

        ArgumentCaptor<FeePlan> saved = ArgumentCaptor.forClass(FeePlan.class);
        verify(plans).save(saved.capture());
        FeePlan plan = saved.getValue();
        assertThat(plan.getNetFee()).isEqualByComparingTo("25000");
        assertThat(plan.getStudentName()).isEqualTo("Asha Patil");
        assertThat(plan.getBatchId()).isEqualTo(3L);
        assertThat(plan.isAutoCreated()).isTrue();
        assertThat(plan.getInstallments()).hasSize(1);
        assertThat(plan.getInstallments().get(0).getDueDate()).isEqualTo(today().plusDays(7));
        assertThat(plan.getInstallments().get(0).getLabel()).isNull();
    }

    @Test
    void anExistingPlanKeepsItsTermsAndOnlyLearnsTheBatch() {
        FeePlan existing = plan(11L, 30L, null);
        when(plans.findFirstByStudentIdAndCourseIdAndStatusNot(30L, 9L, FeePlanStatus.CANCELLED))
                .thenReturn(Optional.of(existing));

        service.createFromEnrollment(placed(3L));

        assertThat(existing.getBatchId()).isEqualTo(3L);
        assertThat(existing.getNetFee()).isEqualByComparingTo("30000");
        verify(courses, never()).publicDetail(any());
    }

    @Test
    void aCourseWithoutAFeeRaisesNoPlan_andAnUnreadableFeeIsRetried() {
        courseFee(BigDecimal.ZERO);
        service.createFromEnrollment(placed(3L));
        verify(plans, never()).save(any());

        when(courses.publicDetail(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.createFromEnrollment(placed(3L))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anAdmissionThatArrivesLaterReplacesAnUntouchedPlanRaisedAtPlacement() {
        FeePlan auto = plan(11L, 30L, 3L);
        auto.setAutoCreated(true);
        when(plans.findFirstByStudentIdAndCourseIdAndStatusNot(30L, 9L, FeePlanStatus.CANCELLED)).thenReturn(Optional.of(auto));
        when(plans.findWithInstallmentsById(11L)).thenReturn(Optional.of(auto));
        when(payments.countByFeePlanIdAndStatus(11L, PaymentStatus.SUCCESS)).thenReturn(0L);

        service.createFromAdmission(new StudentAdmittedEvent("e", Instant.now(), 30L, 7L, "STU-30", "Asha", "a@x", "9",
                9L, 3L, today(), 1L, new BigDecimal("30000"), new BigDecimal("5000"), 2));

        assertThat(auto.getNetFee()).isEqualByComparingTo("25000");
        assertThat(auto.getInstallments()).hasSize(2);
        assertThat(auto.isAutoCreated()).isFalse();
    }

    @Test
    void anAdmissionDoesNotTouchAPlanThatHasPaymentsOrWasChanged() {
        FeePlan auto = plan(11L, 30L, 3L);
        auto.setAutoCreated(true);
        when(plans.findFirstByStudentIdAndCourseIdAndStatusNot(30L, 9L, FeePlanStatus.CANCELLED)).thenReturn(Optional.of(auto));
        when(plans.findWithInstallmentsById(11L)).thenReturn(Optional.of(auto));
        when(payments.countByFeePlanIdAndStatus(11L, PaymentStatus.SUCCESS)).thenReturn(1L);

        service.createFromAdmission(new StudentAdmittedEvent("e", Instant.now(), 30L, 7L, "STU-30", "Asha", "a@x", "9",
                9L, 3L, today(), 1L, new BigDecimal("30000"), new BigDecimal("5000"), 2));

        assertThat(auto.getNetFee()).isEqualByComparingTo("30000");
        verify(plans, never()).save(any());
    }

    // -----------------------------------------------------------------
    // Extra charges
    // -----------------------------------------------------------------

    @Test
    void aChargeIsAnInstallmentOfItsOwn_theTotalGrowsAndTheStudentIsTold() {
        FeePlan plan = plan(11L, 30L, 3L);
        when(plans.findWithInstallmentsById(11L)).thenReturn(Optional.of(plan));
        when(plans.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(payments.findByFeePlanIdOrderByPaymentDateAscIdAsc(11L)).thenReturn(List.of());

        var response = service.addCharge(11L, new ChargeRequest("  Development fee ", new BigDecimal("500"), today().plusDays(10)));

        assertThat(plan.getTotalFee()).isEqualByComparingTo("30500");
        assertThat(plan.getNetFee()).isEqualByComparingTo("30500");
        assertThat(plan.getInstallments()).hasSize(4);
        var charge = plan.getInstallments().get(3);
        assertThat(charge.getLabel()).isEqualTo("Development fee");
        assertThat(charge.getInstallmentNo()).isEqualTo(4);
        assertThat(response.installments()).extracting("label").contains("Development fee");
        verify(events).notifyUsers(any(), any(), any(), any(), any());
    }

    @Test
    void aSettledPlanOwesAgainWhenAChargeIsAdded_andACancelledOneCannotBeCharged() {
        FeePlan settled = plan(11L, 30L, 3L);
        settled.setStatus(FeePlanStatus.SETTLED);
        when(plans.findWithInstallmentsById(11L)).thenReturn(Optional.of(settled));
        when(plans.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(payments.findByFeePlanIdOrderByPaymentDateAscIdAsc(11L)).thenReturn(List.of());
        service.addCharge(11L, new ChargeRequest("Book", new BigDecimal("300"), today()));
        assertThat(settled.getStatus()).isEqualTo(FeePlanStatus.ACTIVE);

        FeePlan cancelled = plan(12L, 31L, 3L);
        cancelled.setStatus(FeePlanStatus.CANCELLED);
        when(plans.findWithInstallmentsById(12L)).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> service.addCharge(12L, new ChargeRequest("Book", new BigDecimal("300"), today())))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aChargeCannotBeDatedInThePast() {
        when(plans.findWithInstallmentsById(11L)).thenReturn(Optional.of(plan(11L, 30L, 3L)));

        assertThatThrownBy(() -> service.addCharge(11L, new ChargeRequest("Book", new BigDecimal("300"), today().minusDays(1))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("past");
    }

    @Test
    void aBatchChargeReachesEveryStudentOfThatBatchWhoIsNotCancelled() {
        FeePlan one = plan(11L, 30L, 3L);
        FeePlan two = plan(12L, 31L, 3L);
        when(plans.findByBatchIdAndStatusNot(3L, FeePlanStatus.CANCELLED)).thenReturn(List.of(one, two));

        var result = service.addChargeToBatch(new BatchChargeRequest(3L, "Exam fee", new BigDecimal("750"), today().plusDays(5)));

        assertThat(result.students()).isEqualTo(2);
        assertThat(one.getNetFee()).isEqualByComparingTo("30750");
        assertThat(two.getInstallments().get(3).getLabel()).isEqualTo("Exam fee");
        verify(events, times(2)).notifyUsers(any(), any(), any(), any(), any());
    }

    @Test
    void editingTheCourseFeeKeepsTheExtraChargesWhereTheyAre() {
        FeePlan plan = plan(11L, 30L, 3L);
        plan.addCharge("Development fee", new BigDecimal("500"), today().plusDays(10));
        when(plans.findWithInstallmentsById(11L)).thenReturn(Optional.of(plan));
        when(plans.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(payments.findByFeePlanIdOrderByPaymentDateAscIdAsc(11L)).thenReturn(List.of());
        AppPrincipal desk = new AppPrincipal(1L, "f@x", "F", "FINANCE", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(desk, null, desk.authorities()));
        try {
            // The desk lowers the whole plan (course fee 30,000 plus the 500 charge) to 20,500: the course fee is 20,000.
            service.update(11L, new FeePlanRequest(null, null, null, new BigDecimal("20500"), BigDecimal.ZERO, 2, today(), null, null, null));

            assertThat(plan.getNetFee()).isEqualByComparingTo("20500");
            assertThat(plan.getInstallments()).hasSize(3);
            assertThat(plan.getInstallments().get(2).getLabel()).isEqualTo("Development fee");
            assertThat(plan.getInstallments().get(2).getAmount()).isEqualByComparingTo("500");
            assertThat(plan.getInstallments().stream().filter(i -> i.getLabel() == null)
                    .map(i -> i.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("20000");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
