package com.itilms.finance.online;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.StudentAdmittedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.Roles;
import com.itilms.common.security.SecurityUtils;
import com.itilms.finance.client.AdmissionClient;
import com.itilms.finance.client.BatchClient;
import com.itilms.finance.client.CourseClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.config.RazorpayProperties;
import com.itilms.finance.dto.request.OnlineCheckoutDtos.CheckoutConfig;
import com.itilms.finance.dto.request.OnlineCheckoutDtos.ConfirmRequest;
import com.itilms.finance.dto.request.OnlineCheckoutDtos.CreateOrderRequest;
import com.itilms.finance.dto.request.OnlineCheckoutDtos.FeeOrderRequest;
import com.itilms.finance.dto.request.OnlineCheckoutDtos.OrderCreated;
import com.itilms.finance.dto.request.OnlineCheckoutDtos.OrderStatus;
import com.itilms.finance.dto.request.RecordPaymentRequest;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.FeePlanStatus;
import com.itilms.finance.entity.OnlineOrder;
import com.itilms.finance.ledger.FeeLedger;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.repository.OnlineOrderRepository;
import com.itilms.finance.service.FeePlanService;
import com.itilms.finance.service.PaymentService;

import lombok.extern.slf4j.Slf4j;

/**
 * Buying a course online.
 *
 * <p>The server decides everything that matters: the amount is the course fee read from course-service, the order
 * belongs to the signed-in student, and a payment counts only when Razorpay's signature over (order id | payment id)
 * checks out. What the browser says about "success" is never trusted. Once paid, the fee plan and the payment (with
 * its receipt) are recorded, then batch-service places the student, so the course opens at once. If placing fails
 * the order stays PAID and the institute is told: the money is never left unaccounted for.
 */
@Slf4j
@Service
public class OnlineCheckoutService {

    private static final String SERVICE_NAME = "finance-service";

    private final RazorpayProperties razorpay;
    private final RazorpayGateway gateway;
    private final OnlineOrderRepository orders;
    private final FeePlanRepository plans;
    private final PaymentRepository paymentRepository;
    private final FeePlanService feePlans;
    private final PaymentService payments;
    private final CourseClient courses;
    private final BatchClient batches;
    private final AdmissionClient admission;
    private final FinanceProperties props;
    private final EventPublisher events;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public OnlineCheckoutService(RazorpayProperties razorpay, RazorpayGateway gateway, OnlineOrderRepository orders,
                                 FeePlanRepository plans, PaymentRepository paymentRepository,
                                 FeePlanService feePlans, PaymentService payments,
                                 CourseClient courses, BatchClient batches, AdmissionClient admission,
                                 FinanceProperties props, EventPublisher events, ObjectMapper json,
                                 PlatformTransactionManager txm) {
        this.razorpay = razorpay;
        this.gateway = gateway;
        this.orders = orders;
        this.plans = plans;
        this.paymentRepository = paymentRepository;
        this.feePlans = feePlans;
        this.payments = payments;
        this.courses = courses;
        this.batches = batches;
        this.admission = admission;
        this.props = props;
        this.events = events;
        this.json = json;
        this.tx = new TransactionTemplate(txm);
    }

    public CheckoutConfig config() {
        return new CheckoutConfig(razorpay.enabled(), razorpay.enabled() ? razorpay.getKeyId() : null);
    }

    // -----------------------------------------------------------------
    // Student: start a purchase
    // -----------------------------------------------------------------

    public OrderCreated createOrder(CreateOrderRequest request) {
        if (!razorpay.enabled()) {
            throw new BusinessRuleException("Online payment is not set up yet. Please send an inquiry instead.");
        }
        AppPrincipal me = student();
        Long studentId = me.profileId();

        CourseClient.CourseDetail detail = courses.get(request.courseId());
        CourseClient.Course course = detail == null ? null : detail.course();
        if (course == null) {
            throw new BusinessRuleException("That course could not be read right now. Please try again shortly.");
        }
        if (!"PUBLISHED".equals(course.status())) {
            throw new BusinessRuleException("This course is not open for enrolment.");
        }
        BigDecimal fee = course.fee() == null ? BigDecimal.ZERO : course.fee().setScale(2, RoundingMode.HALF_UP);
        if (fee.signum() <= 0) {
            throw new BusinessRuleException("The fee for this course is not set yet. Please send an inquiry.");
        }
        if (plans.existsByStudentIdAndCourseIdAndStatusNot(studentId, course.id(), FeePlanStatus.CANCELLED)) {
            throw new BusinessRuleException(
                    "You already have a fee plan for this course. Pay your installments from the Fees page.");
        }
        if (batches.openBatch(course.id(), request.batchId()) == null) {
            throw new BusinessRuleException(
                    "No batch of this course has a free seat right now. Please send an inquiry and we will call you.");
        }

        String receipt = "crs%d-st%d-%d".formatted(course.id(), studentId, Instant.now().getEpochSecond());
        String razorpayOrderId = gateway.createOrder(fee, props.getCurrency(), receipt);
        orders.save(OnlineOrder.builder()
                .razorpayOrderId(razorpayOrderId)
                .studentId(studentId)
                .studentUserId(me.userId())
                .courseId(course.id())
                .courseTitle(course.title())
                .batchId(request.batchId())
                .amount(fee)
                .currency(props.getCurrency())
                .build());
        return new OrderCreated(razorpayOrderId, razorpay.getKeyId(), fee.movePointRight(2).longValueExact(),
                props.getCurrency(), course.title());
    }

    /**
     * Starts a payment against a fee plan the student already has: the whole balance, or any part of it (an
     * installment). The student must own the plan, it must still be live, and the amount cannot be more than is owed,
     * so what Razorpay charges is always something the ledger can accept.
     */
    public OrderCreated createFeeOrder(FeeOrderRequest request) {
        if (!razorpay.enabled()) {
            throw new BusinessRuleException("Online payment is not set up yet. Please pay at the institute.");
        }
        AppPrincipal me = student();
        FeePlan plan = plans.findWithInstallmentsById(request.feePlanId())
                .filter(p -> p.getStudentId().equals(me.profileId()))
                .orElseThrow(() -> new ResourceNotFoundException("Fee plan", request.feePlanId()));
        if (plan.getStatus() != FeePlanStatus.ACTIVE) {
            throw new BusinessRuleException("There is nothing to pay on this fee plan.");
        }
        BigDecimal outstanding = FeeLedger.of(plan,
                paymentRepository.findByFeePlanIdOrderByPaymentDateAscIdAsc(plan.getId()),
                LocalDate.now(props.getZone())).outstanding();
        if (outstanding.signum() <= 0) {
            throw new BusinessRuleException("Your fees for this course are fully paid.");
        }
        BigDecimal amount = request.amount() == null ? outstanding : request.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(new BigDecimal("1.00")) < 0) {
            throw new BusinessRuleException("The smallest online payment is 1 rupee.");
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessRuleException("That is more than the %s %s still owed."
                    .formatted(props.getCurrencySymbol(), outstanding.toPlainString()));
        }

        String receipt = "fee%d-st%d-%d".formatted(plan.getId(), me.profileId(), Instant.now().getEpochSecond());
        String razorpayOrderId = gateway.createOrder(amount, plan.getCurrency(), receipt);
        String title = courseTitle(plan.getCourseId());
        orders.save(OnlineOrder.builder()
                .razorpayOrderId(razorpayOrderId)
                .kind(OnlineOrder.Kind.FEES)
                .studentId(me.profileId())
                .studentUserId(me.userId())
                .courseId(plan.getCourseId())
                .courseTitle(title)
                .feePlanId(plan.getId())
                .amount(amount)
                .currency(plan.getCurrency())
                .build());
        return new OrderCreated(razorpayOrderId, razorpay.getKeyId(), amount.movePointRight(2).longValueExact(),
                plan.getCurrency(), "Fee payment: " + title);
    }

    // -----------------------------------------------------------------
    // Student: the payment finished in the browser
    // -----------------------------------------------------------------

    public OrderStatus confirm(ConfirmRequest request) {
        OnlineOrder order = own(request.orderId());
        if (order.getStatus() == OnlineOrder.Status.CREATED) {
            if (!gateway.validPayment(request.orderId(), request.paymentId(), request.signature())) {
                log.warn("Payment signature mismatch for order {}", request.orderId());
                throw new BusinessRuleException(
                        "The payment could not be verified. If money was deducted, contact the institute with the payment id "
                                + request.paymentId() + ".");
            }
            try {
                tx.executeWithoutResult(status -> recordPayment(request.orderId(), request.paymentId()));
            } catch (RuntimeException e) {
                log.error("Order {} was paid ({}) but could not be recorded", request.orderId(), request.paymentId(), e);
                tellStaff("Online payment needs attention",
                        "Razorpay payment %s for order %s was received but could not be recorded: %s"
                                .formatted(request.paymentId(), request.orderId(), e.getMessage()));
                throw new BusinessRuleException("Your payment was received but could not be recorded yet. The institute "
                        + "has been told. Payment id: " + request.paymentId());
            }
        }
        return finish(request.orderId());
    }

    /** Places a paid student in a batch. Safe to call again: it does nothing once the student is enrolled. */
    public OrderStatus finish(String orderId) {
        OnlineOrder order = own(orderId);
        if (order.getStatus() == OnlineOrder.Status.CREATED) {
            throw new BusinessRuleException("This order has not been paid.");
        }
        if (order.getKind() == OnlineOrder.Kind.FEES) {
            return status(order, null, "Payment received. Your receipt has been emailed to you and is under Fees.");
        }
        if (order.getStatus() == OnlineOrder.Status.ENROLLED) {
            return status(order, null, "You are enrolled. The course is open in My courses.");
        }
        BatchClient.PaidJoin placed = null;
        try {
            placed = batches.paidJoin(new BatchClient.PaidJoinRequest(order.getCourseId(), orderId));
        } catch (RuntimeException e) {
            log.warn("Placing paid order {} in a batch failed", orderId, e);
        }
        final BatchClient.PaidJoin joined = placed;
        if (joined == null) {
            tellStaff("Paid student needs a batch",
                    "%s paid %s for %s (order %s) but could not be placed in a batch."
                            .formatted(order.getStudentId(), order.getAmount().toPlainString(), order.getCourseTitle(), orderId));
            return status(order, null, "Payment received. The institute will add you to a batch shortly.");
        }
        tx.executeWithoutResult(s -> orders.lockByRazorpayOrderId(orderId).ifPresent(o -> {
            o.setStatus(OnlineOrder.Status.ENROLLED);
            o.setBatchId(joined.batchId());
        }));
        return status(orders.findByRazorpayOrderId(orderId).orElse(order), joined.batchCode(),
                "Payment received and you are enrolled. The course is open in My courses.");
    }

    /** Fee plan, payment and receipt, in one transaction, exactly once per order. */
    private void recordPayment(String orderId, String paymentId) {
        OnlineOrder order = orders.lockByRazorpayOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (order.getStatus() != OnlineOrder.Status.CREATED) {
            return;
        }
        if (order.getKind() == OnlineOrder.Kind.FEES) {
            var payment = payments.record(new RecordPaymentRequest(order.getFeePlanId(), order.getAmount(), null, "ONLINE",
                    paymentId, "Razorpay order " + orderId));
            order.setRazorpayPaymentId(paymentId);
            order.setPaymentId(payment.id());
            order.setPaidAt(Instant.now());
            order.setStatus(OnlineOrder.Status.PAID);
            events.audit(SERVICE_NAME, "ONLINE_FEE_PAYMENT_RECEIVED", "OnlineOrder", order.getId(), null,
                    java.util.Map.of("orderId", orderId, "paymentId", paymentId, "amount", order.getAmount().toPlainString()));
            return;
        }
        AdmissionClient.StudentSummary student = admission.lookup(List.of(order.getStudentId())).stream()
                .findFirst().orElseThrow(() -> new BusinessRuleException("The student record could not be read."));

        feePlans.createFromAdmission(new StudentAdmittedEvent(DomainEvent.newId(), Instant.now(),
                student.id(), student.userId(), student.studentCode(), student.fullName(), null, null,
                order.getCourseId(), order.getBatchId(), LocalDate.now(props.getZone()), student.userId(),
                order.getAmount(), BigDecimal.ZERO, 1));
        FeePlan plan = plans.findFirstByStudentIdAndCourseIdAndStatusNot(
                        order.getStudentId(), order.getCourseId(), FeePlanStatus.CANCELLED)
                .orElseThrow(() -> new BusinessRuleException("The fee plan could not be created."));
        var payment = payments.record(new RecordPaymentRequest(plan.getId(), order.getAmount(), null, "ONLINE", paymentId,
                "Razorpay order " + orderId));

        order.setRazorpayPaymentId(paymentId);
        order.setFeePlanId(plan.getId());
        order.setPaymentId(payment.id());
        order.setPaidAt(Instant.now());
        order.setStatus(OnlineOrder.Status.PAID);
        events.audit(SERVICE_NAME, "ONLINE_PAYMENT_RECEIVED", "OnlineOrder", order.getId(), null,
                java.util.Map.of("orderId", orderId, "paymentId", paymentId, "amount", order.getAmount().toPlainString()));
    }

    // -----------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------

    /** The order's owner, or staff. Anyone else is told it does not exist. */
    public OrderStatus get(String orderId) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        OnlineOrder order = orders.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        boolean owner = me.isStudent() && order.getStudentId().equals(me.profileId());
        if (!owner && !me.isStaff()) {
            throw new ResourceNotFoundException("Order", orderId);
        }
        return status(order, null, null);
    }

    // -----------------------------------------------------------------
    // Razorpay webhook: a safety net, not the main path
    // -----------------------------------------------------------------

    /**
     * Razorpay reports a captured payment. Placing the student needs their sign-in, which a webhook does not have, so
     * this only warns the institute when an order was paid but the student's browser never confirmed it.
     */
    public void webhook(String body, String signature) {
        if (!gateway.validWebhook(body, signature)) {
            throw new ForbiddenOperationException("Invalid webhook signature.");
        }
        try {
            JsonNode root = json.readTree(body);
            if (!"payment.captured".equals(root.path("event").asText())) {
                return;
            }
            JsonNode payment = root.path("payload").path("payment").path("entity");
            String orderId = payment.path("order_id").asText("");
            orders.findByRazorpayOrderId(orderId)
                    .filter(o -> o.getStatus() == OnlineOrder.Status.CREATED)
                    .ifPresent(o -> tellStaff("Online payment not confirmed",
                            "Razorpay captured payment %s for order %s (%s, student %d) but the student's browser did not confirm "
                                    .formatted(payment.path("id").asText(), orderId, o.getCourseTitle(), o.getStudentId())
                                    + "it. Check Razorpay and record the payment or place the student by hand."));
        } catch (java.io.IOException e) {
            throw new BusinessRuleException("The webhook body could not be read.");
        }
    }

    // -----------------------------------------------------------------

    private AppPrincipal student() {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        if (!me.isStudent() || me.profileId() == null) {
            throw new ForbiddenOperationException("Only a student can buy a course.");
        }
        return me;
    }

    private OnlineOrder own(String orderId) {
        AppPrincipal me = student();
        OnlineOrder order = orders.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (!order.getStudentId().equals(me.profileId())) {
            throw new ResourceNotFoundException("Order", orderId);
        }
        return order;
    }

    /**
     * What is reported about an order. A payment against a fee plan is reported as FEES_PAID, never PAID or ENROLLED:
     * batch-service places a student in a batch only on a paid course purchase, and paying an installment must not be
     * usable as one.
     */
    private static OrderStatus status(OnlineOrder o, String batchCode, String message) {
        String state = o.getKind() == OnlineOrder.Kind.FEES && o.getStatus() != OnlineOrder.Status.CREATED
                ? "FEES_PAID" : o.getStatus().name();
        return new OrderStatus(o.getRazorpayOrderId(), state, o.getStudentId(), o.getCourseId(),
                o.getBatchId(), batchCode, message);
    }

    private String courseTitle(Long courseId) {
        try {
            CourseClient.CourseDetail detail = courses.get(courseId);
            if (detail != null && detail.course() != null && detail.course().title() != null) {
                return detail.course().title();
            }
        } catch (RuntimeException e) {
            // fall through to the generic wording
        }
        return "your course fee";
    }

    private void tellStaff(String title, String message) {
        events.notifyRole(Roles.ADMIN, "PAYMENT", title, message, "/finance");
        events.notifyRole(Roles.COORDINATOR, "PAYMENT", title, message, "/finance");
    }
}
