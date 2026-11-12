package com.itilms.finance.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.finance.client.AdmissionClient;
import com.itilms.finance.client.BatchClient;
import com.itilms.finance.client.CourseClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.config.RazorpayProperties;
import com.itilms.finance.dto.request.OnlineCheckoutDtos.ConfirmRequest;
import com.itilms.finance.dto.request.OnlineCheckoutDtos.CreateOrderRequest;
import com.itilms.finance.dto.request.OnlineCheckoutDtos.FeeOrderRequest;
import com.itilms.finance.dto.request.RecordPaymentRequest;
import com.itilms.finance.dto.response.PaymentResponse;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.OnlineOrder;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.OnlineOrderRepository;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.service.FeePlanService;
import com.itilms.finance.service.PaymentService;

class OnlineCheckoutServiceTest {

    private static final long STUDENT = 30L;

    private final RazorpayProperties razorpay = new RazorpayProperties();
    private final RazorpayGateway gateway = mock(RazorpayGateway.class);
    private final OnlineOrderRepository orders = mock(OnlineOrderRepository.class);
    private final FeePlanRepository plans = mock(FeePlanRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final FeePlanService feePlans = mock(FeePlanService.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final CourseClient courses = mock(CourseClient.class);
    private final BatchClient batches = mock(BatchClient.class);
    private final AdmissionClient admission = mock(AdmissionClient.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private OnlineCheckoutService service;

    @BeforeEach
    void setUp() {
        razorpay.setKeyId("rzp_test_x");
        razorpay.setKeySecret("secret");
        PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
        when(txm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new OnlineCheckoutService(razorpay, gateway, orders, plans, paymentRepository, feePlans, payments, courses, batches,
                admission, new FinanceProperties(), events, new ObjectMapper(), txm);
        AppPrincipal me = new AppPrincipal(7L, "a@x", "Asha", "STUDENT", STUDENT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(me, null, me.authorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void publishedCourse(BigDecimal fee) {
        when(courses.get(5L)).thenReturn(new CourseClient.CourseDetail(
                new CourseClient.Course(5L, "Java Full Stack", "PUBLISHED", fee)));
    }

    private static OnlineOrder order(OnlineOrder.Status status) {
        return OnlineOrder.builder().razorpayOrderId("order_1").studentId(STUDENT).courseId(5L)
                .courseTitle("Java Full Stack").amount(new BigDecimal("15000.00")).currency("INR").status(status).build();
    }

    @Test
    void theAmountIsTheCourseFeeFromTheCatalogNotFromTheBrowser() {
        publishedCourse(new BigDecimal("15000"));
        when(plans.existsByStudentIdAndCourseIdAndStatusNot(any(), any(), any())).thenReturn(false);
        when(batches.openBatch(5L, null)).thenReturn(new BatchClient.OpenBatch(9L, "B-9", 3));
        when(gateway.createOrder(any(), anyString(), anyString())).thenReturn("order_1");

        var created = service.createOrder(new CreateOrderRequest(5L, null));

        assertThat(created.amountPaise()).isEqualTo(1_500_000L);
        assertThat(created.orderId()).isEqualTo("order_1");
        assertThat(created.keyId()).isEqualTo("rzp_test_x");
        verify(orders).save(any(OnlineOrder.class));
    }

    @Test
    void noSaleWhenNoBatchHasASeat() {
        publishedCourse(new BigDecimal("15000"));
        when(batches.openBatch(5L, null)).thenReturn(null);

        assertThatThrownBy(() -> service.createOrder(new CreateOrderRequest(5L, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("free seat");
        verify(gateway, never()).createOrder(any(), anyString(), anyString());
    }

    @Test
    void noSaleWhenTheCourseHasNoFeeOrIsOffline() {
        publishedCourse(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.createOrder(new CreateOrderRequest(5L, null)))
                .isInstanceOf(BusinessRuleException.class);

        razorpay.setKeyId("");
        assertThatThrownBy(() -> service.createOrder(new CreateOrderRequest(5L, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not set up");
    }

    @Test
    void aBadSignatureRecordsNothing() {
        when(orders.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(order(OnlineOrder.Status.CREATED)));
        when(gateway.validPayment("order_1", "pay_1", "bad")).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(new ConfirmRequest("order_1", "pay_1", "bad")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("could not be verified");
        verify(payments, never()).record(any());
        verify(batches, never()).paidJoin(any());
    }

    @Test
    void anotherStudentsOrderIsNotFound() {
        OnlineOrder theirs = order(OnlineOrder.Status.CREATED);
        theirs.setStudentId(99L);
        when(orders.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.confirm(new ConfirmRequest("order_1", "pay_1", "sig")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aGoodPaymentRecordsTheFeeAndPlacesTheStudent() {
        OnlineOrder o = order(OnlineOrder.Status.CREATED);
        when(orders.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(o));
        when(orders.lockByRazorpayOrderId("order_1")).thenReturn(Optional.of(o));
        when(gateway.validPayment("order_1", "pay_1", "sig")).thenReturn(true);
        when(admission.lookup(List.of(STUDENT))).thenReturn(List.of(
                new AdmissionClient.StudentSummary(STUDENT, 7L, "STU-30", "Asha Patil", "ACTIVE", "9800000000", "Maharashtra")));
        FeePlan plan = FeePlan.builder().studentId(STUDENT).courseId(5L).build();
        plan.setId(11L);
        when(plans.findFirstByStudentIdAndCourseIdAndStatusNot(any(), any(), any())).thenReturn(Optional.of(plan));
        PaymentResponse paid = mock(PaymentResponse.class);
        when(paid.id()).thenReturn(21L);
        when(payments.record(any(RecordPaymentRequest.class))).thenReturn(paid);
        when(batches.paidJoin(any())).thenReturn(new BatchClient.PaidJoin(9L, "B-9", false));

        var result = service.confirm(new ConfirmRequest("order_1", "pay_1", "sig"));

        assertThat(result.status()).isEqualTo("ENROLLED");
        assertThat(result.batchCode()).isEqualTo("B-9");
        assertThat(o.getFeePlanId()).isEqualTo(11L);
        assertThat(o.getPaymentId()).isEqualTo(21L);
        verify(payments).record(any(RecordPaymentRequest.class));
    }

    @Test
    void whenPlacingFailsTheOrderStaysPaidAndStaffAreTold() {
        OnlineOrder o = order(OnlineOrder.Status.PAID);
        when(orders.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(o));
        when(batches.paidJoin(any())).thenReturn(null);

        var result = service.finish("order_1");

        assertThat(result.status()).isEqualTo("PAID");
        assertThat(result.message()).contains("shortly");
        verify(events, org.mockito.Mockito.atLeastOnce()).notifyRole(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void aSecondConfirmationDoesNotRecordTheMoneyAgain() {
        OnlineOrder o = order(OnlineOrder.Status.ENROLLED);
        when(orders.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(o));

        var result = service.confirm(new ConfirmRequest("order_1", "pay_1", "sig"));

        assertThat(result.status()).isEqualTo("ENROLLED");
        verify(payments, never()).record(any());
    }

    // -----------------------------------------------------------------
    // Paying a fee plan that already exists (an installment, or the balance)
    // -----------------------------------------------------------------

    /** A 30,000 plan in three installments of 10,000, nothing paid yet. */
    private FeePlan feePlan(long studentId) {
        FeePlan plan = FeePlan.builder().studentId(studentId).courseId(5L).currency("INR").build();
        plan.setId(11L);
        plan.setTerms(new BigDecimal("30000.00"), BigDecimal.ZERO);
        plan.replaceInstallments(new java.util.ArrayList<>(com.itilms.finance.ledger.ScheduleBuilder.evenSplit(
                plan.getNetFee(), 3, java.time.LocalDate.of(2026, 1, 1))));
        return plan;
    }

    private void ownPlan() {
        when(plans.findWithInstallmentsById(11L)).thenReturn(Optional.of(feePlan(STUDENT)));
        when(paymentRepository.findByFeePlanIdOrderByPaymentDateAscIdAsc(11L)).thenReturn(List.of());
        when(gateway.createOrder(any(), anyString(), anyString())).thenReturn("order_fee");
    }

    @Test
    void anInstallmentCanBePaidOnline_andWithNoAmountItIsTheWholeBalance() {
        ownPlan();

        var installment = service.createFeeOrder(new FeeOrderRequest(11L, new BigDecimal("10000")));
        var balance = service.createFeeOrder(new FeeOrderRequest(11L, null));

        assertThat(installment.amountPaise()).isEqualTo(1_000_000L);
        assertThat(balance.amountPaise()).isEqualTo(3_000_000L);
        ArgumentCaptor<OnlineOrder> saved = ArgumentCaptor.forClass(OnlineOrder.class);
        verify(orders, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(o -> {
            assertThat(o.getKind()).isEqualTo(OnlineOrder.Kind.FEES);
            assertThat(o.getFeePlanId()).isEqualTo(11L);
        });
    }

    @Test
    void youCannotPayMoreThanIsOwed_orSomeoneElsesPlan_orNothing() {
        ownPlan();
        assertThatThrownBy(() -> service.createFeeOrder(new FeeOrderRequest(11L, new BigDecimal("30000.01"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("still owed");

        when(plans.findWithInstallmentsById(12L)).thenReturn(Optional.of(feePlan(99L)));
        assertThatThrownBy(() -> service.createFeeOrder(new FeeOrderRequest(12L, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        FeePlan settled = feePlan(STUDENT);
        settled.setStatus(com.itilms.finance.entity.FeePlanStatus.SETTLED);
        when(plans.findWithInstallmentsById(13L)).thenReturn(Optional.of(settled));
        assertThatThrownBy(() -> service.createFeeOrder(new FeeOrderRequest(13L, null)))
                .isInstanceOf(BusinessRuleException.class);
        verify(gateway, org.mockito.Mockito.never()).createOrder(any(), anyString(), anyString());
    }

    @Test
    void aVerifiedFeePaymentIsRecordedAgainstThePlanAndNeverPlacesAnyoneInABatch() {
        OnlineOrder o = OnlineOrder.builder().razorpayOrderId("order_fee").kind(OnlineOrder.Kind.FEES).studentId(STUDENT)
                .courseId(5L).courseTitle("Java Full Stack").feePlanId(11L).amount(new BigDecimal("10000.00"))
                .currency("INR").build();
        when(orders.findByRazorpayOrderId("order_fee")).thenReturn(Optional.of(o));
        when(orders.lockByRazorpayOrderId("order_fee")).thenReturn(Optional.of(o));
        when(gateway.validPayment("order_fee", "pay_9", "sig")).thenReturn(true);
        PaymentResponse paid = mock(PaymentResponse.class);
        when(paid.id()).thenReturn(31L);
        when(payments.record(any(RecordPaymentRequest.class))).thenReturn(paid);

        var result = service.confirm(new ConfirmRequest("order_fee", "pay_9", "sig"));

        assertThat(result.status()).isEqualTo("FEES_PAID");
        assertThat(o.getStatus()).isEqualTo(OnlineOrder.Status.PAID);
        assertThat(o.getPaymentId()).isEqualTo(31L);
        ArgumentCaptor<RecordPaymentRequest> request = ArgumentCaptor.forClass(RecordPaymentRequest.class);
        verify(payments).record(request.capture());
        assertThat(request.getValue().feePlanId()).isEqualTo(11L);
        assertThat(request.getValue().method()).isEqualTo("ONLINE");
        verify(feePlans, org.mockito.Mockito.never()).createFromAdmission(any());
        verify(batches, never()).paidJoin(any());
    }

    @Test
    void aFeePaymentIsNeverReportedAsAPaidCoursePurchase_soItCannotBuyAPlaceInABatch() {
        OnlineOrder o = OnlineOrder.builder().razorpayOrderId("order_fee").kind(OnlineOrder.Kind.FEES).studentId(STUDENT)
                .courseId(5L).feePlanId(11L).amount(new BigDecimal("1.00")).currency("INR")
                .status(OnlineOrder.Status.PAID).build();
        when(orders.findByRazorpayOrderId("order_fee")).thenReturn(Optional.of(o));

        assertThat(service.get("order_fee").status()).isEqualTo("FEES_PAID");
    }
}
