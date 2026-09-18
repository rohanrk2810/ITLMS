package com.itilms.finance.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.FeePlanCreatedEvent;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.StudentAdmittedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.finance.client.AdmissionClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.dto.request.FeePlanRequest;
import com.itilms.finance.dto.response.FeePlanResponse;
import com.itilms.finance.dto.response.PaymentResponse;
import com.itilms.finance.entity.FeeInstallment;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.FeePlanStatus;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.PaymentStatus;
import com.itilms.finance.ledger.FeeLedger;
import com.itilms.finance.ledger.ScheduleBuilder;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.service.FeePlanService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeePlanServiceImpl implements FeePlanService {

    private static final String SERVICE_NAME = "finance-service";

    private final FeePlanRepository planRepository;
    private final PaymentRepository paymentRepository;
    private final AdmissionClient admissionClient;
    private final FinanceProperties props;
    private final EventPublisher events;

    // -----------------------------------------------------------------
    // Raising plans
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public void createFromAdmission(StudentAdmittedEvent event) {
        if (!event.hasFeePlan()) {
            return;
        }
        if (planRepository.existsByStudentIdAndCourseIdAndStatusNot(
                event.studentId(), event.courseId(), FeePlanStatus.CANCELLED)) {
            log.debug("Student {} already has a fee plan for course {}; admission event ignored",
                    event.studentId(), event.courseId());
            return;
        }
        BigDecimal discount = event.discount() == null ? BigDecimal.ZERO : event.discount();
        if (discount.compareTo(event.totalFee()) > 0) {
            // A fee plan with a negative net would be a debt the institute owes.
            // Leave it for the finance desk rather than invent a number.
            log.error("Admission of student {} carried a discount larger than the fee; no plan raised",
                    event.studentId());
            return;
        }

        FeePlan plan = FeePlan.builder()
                .studentId(event.studentId())
                .studentUserId(event.userId())
                .studentCode(event.studentCode())
                .studentName(event.fullName())
                .courseId(event.courseId())
                .batchId(event.batchId())
                .currency(props.getCurrency())
                .build();
        plan.setTerms(event.totalFee(), discount);

        LocalDate firstDue = event.admissionDate() != null ? event.admissionDate() : today();
        int count = event.installments() == null || event.installments() < 1 ? 1 : event.installments();
        plan.replaceInstallments(schedule(plan.getNetFee(), count, firstDue, null));
        plan.setStatus(plan.getNetFee().signum() == 0 ? FeePlanStatus.SETTLED : FeePlanStatus.ACTIVE);

        plan = planRepository.save(plan);
        announce(plan);
        log.info("Raised fee plan {} for student {} from admission", plan.getId(), plan.getStudentId());
    }

    @Override
    @Transactional
    public FeePlanResponse create(FeePlanRequest request) {
        if (request.studentId() == null || request.courseId() == null) {
            throw new BusinessRuleException("A fee plan needs a student and a course.");
        }
        AdmissionClient.StudentSummary student = admissionClient.lookup(List.of(request.studentId())).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Student", request.studentId()));

        if (planRepository.existsByStudentIdAndCourseIdAndStatusNot(
                request.studentId(), request.courseId(), FeePlanStatus.CANCELLED)) {
            throw new DuplicateResourceException(
                    "This student already has a fee plan for this course. Update it instead.");
        }
        requireValidTerms(request.totalFee(), request.discount());

        FeePlan plan = FeePlan.builder()
                .studentId(student.id())
                .studentUserId(student.userId())
                .studentCode(student.studentCode())
                .studentName(student.fullName())
                .courseId(request.courseId())
                .batchId(request.batchId())
                .currency(props.getCurrency())
                .notes(trim(request.notes()))
                .build();
        plan.setTerms(request.totalFee(), request.discount());
        plan.replaceInstallments(schedule(plan.getNetFee(), request.installmentCount(),
                request.firstDueDate(), request.installments()));
        plan.setStatus(plan.getNetFee().signum() == 0 ? FeePlanStatus.SETTLED : FeePlanStatus.ACTIVE);

        plan = planRepository.save(plan);
        announce(plan);
        events.audit(SERVICE_NAME, "FEE_PLAN_CREATED", "FeePlan", plan.getId(), null,
                Map.of("studentId", plan.getStudentId(), "netFee", plan.getNetFee().toPlainString()));
        return response(plan, List.of());
    }

    // -----------------------------------------------------------------
    // Changing plans
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public FeePlanResponse update(Long id, FeePlanRequest request) {
        FeePlan plan = requirePlan(id);
        if (plan.getStatus() == FeePlanStatus.CANCELLED) {
            throw new BusinessRuleException("A cancelled fee plan cannot be changed.");
        }
        requireValidTerms(request.totalFee(), request.discount());

        List<Payment> payments = paymentRepository.findByFeePlanIdOrderByPaymentDateAscIdAsc(id);
        BigDecimal alreadyPaid = FeeLedger.of(plan, payments, today()).paid();
        BigDecimal newNet = FeePlan.money(request.totalFee())
                .subtract(FeePlan.money(request.discount() == null ? BigDecimal.ZERO : request.discount()));
        if (newNet.compareTo(alreadyPaid) < 0) {
            throw new BusinessRuleException("The student has already paid %s %s, so the net fee cannot be set below that."
                    .formatted(props.getCurrencySymbol(), alreadyPaid.toPlainString()));
        }

        Map<String, Object> before = Map.of("totalFee", plan.getTotalFee().toPlainString(),
                "discount", plan.getDiscount().toPlainString(), "installments", plan.getInstallments().size());
        boolean netChanged = newNet.compareTo(plan.getNetFee()) != 0;
        plan.setTerms(request.totalFee(), request.discount());
        if (request.notes() != null) {
            plan.setNotes(trim(request.notes()));
        }

        boolean newSchedule = (request.installments() != null && !request.installments().isEmpty())
                || request.installmentCount() != null;
        if (newSchedule || netChanged) {
            // Keep the existing shape when only the amount moved: same number of
            // installments from the same first date.
            Integer count = request.installmentCount() != null ? request.installmentCount()
                    : Math.max(1, plan.getInstallments().size());
            LocalDate first = request.firstDueDate() != null ? request.firstDueDate()
                    : plan.getInstallments().stream().map(FeeInstallment::getDueDate)
                            .min(Comparator.naturalOrder()).orElse(today());
            replaceSchedule(plan, schedule(plan.getNetFee(), count, first, request.installments()));
        }

        FeeLedger ledger = FeeLedger.of(plan, payments, today());
        plan.setStatus(ledger.isSettled() ? FeePlanStatus.SETTLED : FeePlanStatus.ACTIVE);
        plan = planRepository.save(plan);

        events.audit(SERVICE_NAME, "FEE_PLAN_UPDATED", "FeePlan", plan.getId(), before,
                Map.of("totalFee", plan.getTotalFee().toPlainString(),
                        "discount", plan.getDiscount().toPlainString(),
                        "installments", plan.getInstallments().size()));
        return response(plan, payments);
    }

    @Override
    @Transactional
    public FeePlanResponse cancel(Long id, String reason) {
        FeePlan plan = requirePlan(id);
        if (plan.getStatus() == FeePlanStatus.CANCELLED) {
            throw new BusinessRuleException("This fee plan is already cancelled.");
        }
        if (paymentRepository.countByFeePlanIdAndStatus(id, PaymentStatus.SUCCESS) > 0) {
            throw new BusinessRuleException(
                    "Money has been received on this plan. Reverse those payments before cancelling it.");
        }
        plan.cancel(reason.trim(), Instant.now());
        plan = planRepository.save(plan);
        events.audit(SERVICE_NAME, "FEE_PLAN_CANCELLED", "FeePlan", id, null, Map.of("reason", reason));
        return response(plan, paymentRepository.findByFeePlanIdOrderByPaymentDateAscIdAsc(id));
    }

    // -----------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public FeePlanResponse get(Long id) {
        FeePlan plan = requirePlan(id);
        SecurityUtils.requireStudentOwnershipOrStaff(plan.getStudentId());
        return response(plan, paymentRepository.findByFeePlanIdOrderByPaymentDateAscIdAsc(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeePlanResponse> forStudent(Long studentId) {
        SecurityUtils.requireStudentOwnershipOrStaff(studentId);
        List<FeePlan> plans = planRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        Map<Long, List<Payment>> payments = paymentsByPlan(plans);
        return plans.stream().map(p -> response(p, payments.getOrDefault(p.getId(), List.of()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeePlanResponse> mine() {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.studentIdOrNull() == null) {
            throw new ForbiddenOperationException("Your account is not linked to a student profile yet.");
        }
        return forStudent(caller.profileId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FeePlanResponse> list(String status, Pageable pageable) {
        Page<FeePlan> page = status == null || status.isBlank()
                ? planRepository.findAllByOrderByCreatedAtDesc(pageable)
                : planRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status), pageable);
        Map<Long, List<Payment>> payments = paymentsByPlan(page.getContent());
        LocalDate today = today();
        // Listing rows carry the figures, not the payment history.
        return PageResponse.from(page, p -> FeePlanResponse.of(p,
                FeeLedger.of(p, payments.getOrDefault(p.getId(), List.of()), today), null));
    }

    // -----------------------------------------------------------------

    /**
     * Builds the schedule, refusing one that does not add up.
     *
     * <p>An explicit schedule must total the net fee to the paisa. A schedule
     * that totals less leaves money that is owed but never falls due; one that
     * totals more chases the student for money they do not owe.
     */
    private List<FeeInstallment> schedule(BigDecimal netFee, Integer count, LocalDate firstDue,
                                          List<FeePlanRequest.InstallmentInput> explicit) {
        if (netFee.signum() == 0) {
            return List.of();
        }
        if (explicit != null && !explicit.isEmpty()) {
            List<FeePlanRequest.InstallmentInput> ordered = explicit.stream()
                    .sorted(Comparator.comparing(FeePlanRequest.InstallmentInput::dueDate)).toList();
            List<FeeInstallment> schedule = new ArrayList<>(ordered.size());
            for (int i = 0; i < ordered.size(); i++) {
                schedule.add(FeeInstallment.builder()
                        .installmentNo(i + 1)
                        .dueDate(ordered.get(i).dueDate())
                        .amount(FeePlan.money(ordered.get(i).amount()))
                        .build());
            }
            BigDecimal total = ScheduleBuilder.total(schedule);
            if (total.compareTo(netFee) != 0) {
                throw new BusinessRuleException("The installments add up to %s %s but the net fee is %s %s."
                        .formatted(props.getCurrencySymbol(), total.toPlainString(),
                                props.getCurrencySymbol(), netFee.toPlainString()));
            }
            return schedule;
        }

        int n = count == null ? 1 : count;
        if (netFee.compareTo(BigDecimal.valueOf(n)) < 0) {
            throw new BusinessRuleException("The fee is too small to split into %d installments.".formatted(n));
        }
        return ScheduleBuilder.evenSplit(netFee, n, firstDue != null ? firstDue : today());
    }

    /**
     * Swaps the schedule in two steps.
     *
     * <p>Hibernate writes new rows before deleting orphaned ones, and the
     * (plan, installment number) key would reject the new installment 1 while
     * the old installment 1 still exists. Flushing the removal first avoids it.
     */
    private void replaceSchedule(FeePlan plan, List<FeeInstallment> schedule) {
        plan.getInstallments().clear();
        planRepository.saveAndFlush(plan);
        plan.getInstallments().addAll(schedule);
    }

    private void requireValidTerms(BigDecimal totalFee, BigDecimal discount) {
        if (discount != null && discount.compareTo(totalFee) > 0) {
            throw new BusinessRuleException("The discount cannot be larger than the fee.");
        }
    }

    private void announce(FeePlan plan) {
        LocalDate firstDue = plan.getInstallments().stream().map(FeeInstallment::getDueDate)
                .min(Comparator.naturalOrder()).orElse(null);
        events.publishAfterCommit(KafkaTopics.FEE_PLAN_CREATED, String.valueOf(plan.getStudentId()),
                new FeePlanCreatedEvent(DomainEvent.newId(), Instant.now(), plan.getId(), plan.getStudentId(),
                        plan.getStudentUserId(), plan.getCourseId(), plan.getNetFee(),
                        plan.getInstallments().size(), firstDue));
        if (plan.getStudentUserId() != null && plan.getNetFee().signum() > 0) {
            events.notifyUsers(List.of(plan.getStudentUserId()), "FEE_PLAN",
                    "Your fee plan",
                    "Net fee %s %s in %d installment(s)%s.".formatted(props.getCurrencySymbol(),
                            plan.getNetFee().toPlainString(), plan.getInstallments().size(),
                            firstDue == null ? "" : ", first due " + firstDue),
                    "/student/fees");
        }
    }

    private FeePlanResponse response(FeePlan plan, List<Payment> payments) {
        return FeePlanResponse.of(plan, FeeLedger.of(plan, payments, today()),
                payments.stream().map(PaymentResponse::from).toList());
    }

    private Map<Long, List<Payment>> paymentsByPlan(List<FeePlan> plans) {
        if (plans.isEmpty()) {
            return Map.of();
        }
        return paymentRepository.findByFeePlanIdIn(plans.stream().map(FeePlan::getId).toList()).stream()
                .collect(Collectors.groupingBy(Payment::getFeePlanId));
    }

    private FeePlan requirePlan(Long id) {
        return planRepository.findWithInstallmentsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fee plan", id));
    }

    private FeePlanStatus parseStatus(String value) {
        try {
            return FeePlanStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Status must be ACTIVE, SETTLED or CANCELLED.");
        }
    }

    private LocalDate today() {
        return LocalDate.now(props.getZone());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
