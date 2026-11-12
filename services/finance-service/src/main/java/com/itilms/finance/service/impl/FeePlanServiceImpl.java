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
import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.common.event.StudentAdmittedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.finance.client.AdmissionClient;
import com.itilms.finance.client.CourseClient;
import com.itilms.finance.dto.request.ChargeDtos.BatchChargeRequest;
import com.itilms.finance.dto.request.ChargeDtos.BatchChargeResult;
import com.itilms.finance.dto.request.ChargeDtos.ChargeRequest;
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
import com.itilms.finance.repository.CourseFeeTemplateRepository;
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
    private final CourseClient courseClient;
    private final FinanceProperties props;
    private final EventPublisher events;
    private final CourseFeeTemplateRepository templates;

    // -----------------------------------------------------------------
    // Raising plans
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public void createFromAdmission(StudentAdmittedEvent event) {
        if (!event.hasFeePlan()) {
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
        var existing = planRepository.findFirstByStudentIdAndCourseIdAndStatusNot(
                event.studentId(), event.courseId(), FeePlanStatus.CANCELLED);
        if (existing.isPresent()) {
            FeePlan plan = planRepository.findWithInstallmentsById(existing.get().getId()).orElse(existing.get());
            // The placement can arrive before the admission. The plan it raised is the plain course fee; the
            // admission carries what was actually agreed (a discount, installments), so it takes over, provided
            // nobody has paid on it or changed it yet.
            if (plan.isAutoCreated()
                    && paymentRepository.countByFeePlanIdAndStatus(plan.getId(), PaymentStatus.SUCCESS) == 0) {
                plan.setTerms(event.totalFee(), discount);
                int count = event.installments() == null || event.installments() < 1 ? 1 : event.installments();
                replaceSchedule(plan, schedule(plan.getNetFee(), count,
                        event.admissionDate() != null ? event.admissionDate() : today(), null));
                plan.setAutoCreated(false);
                plan.setStatus(plan.getNetFee().signum() == 0 ? FeePlanStatus.SETTLED : FeePlanStatus.ACTIVE);
                planRepository.save(plan);
                log.info("Fee plan {} raised at placement was replaced by the terms agreed at admission", plan.getId());
            } else {
                log.debug("Student {} already has a fee plan for course {}; admission event ignored",
                        event.studentId(), event.courseId());
            }
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

    /** Days after placement that the course fee falls due. */
    private static final int AUTO_PLAN_DUE_DAYS = 7;

    @Override
    @Transactional
    public void createFromEnrollment(EnrollmentCreatedEvent event) {
        if (event.studentId() == null || event.courseId() == null) {
            return;
        }
        var existing = planRepository.findFirstByStudentIdAndCourseIdAndStatusNot(
                event.studentId(), event.courseId(), FeePlanStatus.CANCELLED);
        if (existing.isPresent()) {
            FeePlan plan = existing.get();
            // A plan that came from admission or a purchase keeps its terms; it only learns which batch the student
            // is in now (a transfer moves them).
            if (event.batchId() != null && !event.batchId().equals(plan.getBatchId())) {
                plan.setBatchId(event.batchId());
                planRepository.save(plan);
            }
            return;
        }
        CourseClient.CourseDetail detail = courseClient.publicDetail(event.courseId());
        if (detail == null || detail.course() == null) {
            // Throwing lets Kafka deliver the event again once course-service is back, instead of losing it.
            throw new IllegalStateException("The fee of course " + event.courseId() + " could not be read yet");
        }
        BigDecimal fee = detail.course().fee();
        if (fee == null || fee.signum() <= 0) {
            log.info("Course {} has no fee; no plan raised for student {}", event.courseId(), event.studentId());
            return;
        }

        FeePlan plan = FeePlan.builder()
                .studentId(event.studentId())
                .studentUserId(event.userId())
                .studentCode(event.studentCode())
                .studentName(event.studentName())
                .courseId(event.courseId())
                .batchId(event.batchId())
                .currency(props.getCurrency())
                .autoCreated(true)
                .build();
        plan.setTerms(fee, BigDecimal.ZERO);
        var template = templates.findById(event.courseId());
        int count = template.map(t -> t.getInstallmentCount()).orElse(1);
        int firstDueDays = template.map(t -> t.getFirstDueDays()).orElse(AUTO_PLAN_DUE_DAYS);
        plan.replaceInstallments(schedule(plan.getNetFee(), count, today().plusDays(firstDueDays), null));
        // The extra heads the course usually carries (a development fee, a book), each its own labelled installment.
        if (template.isPresent()) {
            for (var c : template.get().getCharges()) {
                plan.addCharge(c.getLabel(), c.getAmount(), today().plusDays(c.getDueDays()));
            }
        }
        plan = planRepository.save(plan);
        announce(plan);
        events.audit(SERVICE_NAME, "FEE_PLAN_RAISED_AT_PLACEMENT", "FeePlan", plan.getId(), null,
                Map.of("studentId", plan.getStudentId(), "courseId", plan.getCourseId(),
                        "netFee", plan.getNetFee().toPlainString()));
        log.info("Raised fee plan {} for student {} when they were placed in batch {}",
                plan.getId(), plan.getStudentId(), event.batchId());
    }

    // -----------------------------------------------------------------
    // Extra charges: a development fee, a book, an exam fee
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public FeePlanResponse addCharge(Long planId, ChargeRequest request) {
        FeePlan plan = requirePlan(planId);
        applyCharge(plan, request.title(), request.amount(), request.dueDate(), Boolean.TRUE.equals(request.gstExempt()));
        plan = planRepository.save(plan);
        return response(plan, paymentRepository.findByFeePlanIdOrderByPaymentDateAscIdAsc(planId));
    }

    @Override
    @Transactional
    public BatchChargeResult addChargeToBatch(BatchChargeRequest request) {
        List<FeePlan> plans = planRepository.findByBatchIdAndStatusNot(request.batchId(), FeePlanStatus.CANCELLED);
        int added = 0;
        for (FeePlan plan : plans) {
            applyCharge(plan, request.title(), request.amount(), request.dueDate(), Boolean.TRUE.equals(request.gstExempt()));
            added++;
        }
        planRepository.saveAll(plans);
        events.audit(SERVICE_NAME, "BATCH_CHARGE_ADDED", "Batch", request.batchId(), null,
                Map.of("title", request.title().trim(), "amount", FeePlan.money(request.amount()).toPlainString(),
                        "students", added));
        return new BatchChargeResult(added, 0);
    }

    /** Adds the charge to a plan and tells the student; the caller saves. */
    private void applyCharge(FeePlan plan, String title, BigDecimal amount, LocalDate dueDate, boolean gstExempt) {
        if (plan.getStatus() == FeePlanStatus.CANCELLED) {
            throw new BusinessRuleException("A cancelled fee plan cannot be charged.");
        }
        if (dueDate.isBefore(today())) {
            throw new BusinessRuleException("The due date cannot be in the past.");
        }
        String label = title.trim();
        plan.addCharge(label, amount, dueDate, gstExempt);
        plan.setAutoCreated(false);
        events.audit(SERVICE_NAME, "FEE_CHARGE_ADDED", "FeePlan", plan.getId(), null,
                Map.of("title", label, "amount", FeePlan.money(amount).toPlainString(), "dueDate", dueDate.toString()));
        if (plan.getStudentUserId() != null) {
            events.notifyUsers(List.of(plan.getStudentUserId()), "FEE_PLAN", "New fee: " + label,
                    "%s %s has been added to your fees, due %s. You can pay it online from Fees."
                            .formatted(props.getCurrencySymbol(), FeePlan.money(amount).toPlainString(), dueDate),
                    "/student/fees");
        }
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
        requireConcessionAllowed(request.totalFee(), request.discount(), BigDecimal.ZERO, request.discountReason());

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
        recordConcession(plan, request.discountReason());
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
        requireConcessionAllowed(request.totalFee(), request.discount(), plan.getDiscount(), request.discountReason());

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
        BigDecimal oldDiscount = plan.getDiscount();
        plan.setTerms(request.totalFee(), request.discount());
        if (plan.getDiscount().compareTo(oldDiscount) != 0) {
            recordConcession(plan, request.discountReason());
        }
        if (request.notes() != null) {
            plan.setNotes(trim(request.notes()));
        }

        boolean newSchedule = (request.installments() != null && !request.installments().isEmpty())
                || request.installmentCount() != null;
        if (newSchedule || netChanged) {
            // Extra charges (a development fee, a book) are not part of the course fee being re-split: they stay as
            // they are, after the course-fee installments.
            List<FeeInstallment> extras = plan.getInstallments().stream().filter(i -> i.getLabel() != null).toList();
            List<FeeInstallment> course = plan.getInstallments().stream().filter(i -> i.getLabel() == null).toList();
            BigDecimal extrasTotal = ScheduleBuilder.total(extras);
            BigDecimal courseNet = plan.getNetFee().subtract(extrasTotal);
            if (courseNet.signum() < 0) {
                throw new BusinessRuleException(
                        "The net fee cannot be less than the extra charges already on this plan (%s %s)."
                                .formatted(props.getCurrencySymbol(), extrasTotal.toPlainString()));
            }
            // Keep the existing shape when only the amount moved: same number of
            // installments from the same first date.
            Integer count = request.installmentCount() != null ? request.installmentCount()
                    : Math.max(1, course.size());
            LocalDate first = request.firstDueDate() != null ? request.firstDueDate()
                    : course.stream().map(FeeInstallment::getDueDate).min(Comparator.naturalOrder()).orElse(today());
            List<FeeInstallment> rebuilt = new ArrayList<>(schedule(courseNet, count, first, request.installments()));
            int number = rebuilt.size();
            for (FeeInstallment extra : extras) {
                rebuilt.add(FeeInstallment.builder().installmentNo(++number).dueDate(extra.getDueDate())
                        .amount(extra.getAmount()).label(extra.getLabel()).build());
            }
            replaceSchedule(plan, rebuilt);
            plan.setAutoCreated(false);
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

    /**
     * A discount is given for a reason, and a large one by the administrator.
     *
     * <p>Only a discount that is new or changed is checked: a plan that already carries one (agreed at admission) can be
     * edited for other reasons without being asked to justify it again.
     */
    private void requireConcessionAllowed(BigDecimal totalFee, BigDecimal discount, BigDecimal previous, String reason) {
        BigDecimal wanted = discount == null ? BigDecimal.ZERO : FeePlan.money(discount);
        if (wanted.signum() <= 0 || wanted.compareTo(previous) == 0) {
            return;
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Say why this discount is given.");
        }
        BigDecimal percent = totalFee.signum() == 0 ? BigDecimal.ZERO
                : wanted.multiply(BigDecimal.valueOf(100)).divide(totalFee, 2, java.math.RoundingMode.HALF_UP);
        if (percent.compareTo(BigDecimal.valueOf(props.getDiscountApprovalPercent())) > 0
                && !SecurityUtils.requirePrincipal().hasRole(com.itilms.common.security.Roles.ADMIN)) {
            throw new BusinessRuleException("A discount above %d%% of the fee must be given by the administrator."
                    .formatted(props.getDiscountApprovalPercent()));
        }
    }

    private void recordConcession(FeePlan plan, String reason) {
        if (plan.getDiscount().signum() > 0) {
            plan.setDiscountReason(trim(reason));
            plan.setDiscountSetBy(SecurityUtils.requirePrincipal().userId());
        } else {
            plan.setDiscountReason(null);
            plan.setDiscountSetBy(null);
        }
    }

    @Override
    @Transactional
    public FeePlanResponse waiveLateFee(Long planId, Long installmentId, String reason) {
        FeePlan plan = requirePlan(planId);
        FeeInstallment charge = plan.getInstallments().stream()
                .filter(i -> installmentId.equals(i.getId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Installment", installmentId));
        if (!charge.isLateFee()) {
            throw new BusinessRuleException("Only a late fee can be waived.");
        }
        List<Payment> payments = paymentRepository.findByFeePlanIdOrderByPaymentDateAscIdAsc(planId);
        FeeLedger.Line line = FeeLedger.of(plan, payments, today()).lineFor(installmentId);
        if (line != null && line.paid().signum() > 0) {
            throw new BusinessRuleException("Money has already been paid towards this late fee, so it cannot be waived.");
        }
        BigDecimal amount = charge.getAmount();
        plan.removeCharge(charge);
        FeeLedger after = FeeLedger.of(plan, payments, today());
        plan.setStatus(after.isSettled() ? FeePlanStatus.SETTLED : FeePlanStatus.ACTIVE);
        plan = planRepository.save(plan);
        events.audit(SERVICE_NAME, "LATE_FEE_WAIVED", "FeePlan", planId, null,
                Map.of("amount", amount.toPlainString(), "reason", reason.trim()));
        if (plan.getStudentUserId() != null) {
            events.notifyUsers(List.of(plan.getStudentUserId()), "FEE_PLAN", "Late fee waived",
                    "The late fee of %s %s has been removed from your fees.".formatted(props.getCurrencySymbol(),
                            amount.toPlainString()), "/student/fees");
        }
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
