package com.itilms.finance.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.InstallmentOverdueEvent;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.security.Roles;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.FeeInstallment;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.ledger.FeeLedger;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The morning fee scan (Doc S16: "Fee due / overdue - Student + finance").
 *
 * <p>Before a due date the student gets a reminder at each configured step -
 * 7, 3 and 1 days by default. The first morning an installment is overdue, the
 * student and the finance desk are told once. Each message is recorded on the
 * installment so it goes out once, not every morning.
 *
 * <p>Amounts in every message are what is still unpaid on the installment, as
 * worked out by {@link FeeLedger}: a student who has paid part of it is not
 * reminded about the whole.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstallmentReminderJob {

    private final FeePlanRepository planRepository;
    private final PaymentRepository paymentRepository;
    private final FinanceProperties props;
    private final EventPublisher events;

    @Scheduled(cron = "${itilms.finance.overdue-scan-cron:0 30 6 * * *}",
            zone = "${itilms.finance.zone:Asia/Kolkata}")
    @Transactional
    public void scan() {
        LocalDate today = LocalDate.now(props.getZone());
        int horizon = props.getDueReminderDays().stream().max(Integer::compare).orElse(0);

        List<FeePlan> plans = planRepository.findActiveWithInstallmentDueBy(today.plusDays(horizon));
        if (plans.isEmpty()) {
            return;
        }
        Map<Long, List<Payment>> payments = paymentRepository
                .findByFeePlanIdIn(plans.stream().map(FeePlan::getId).toList()).stream()
                .collect(Collectors.groupingBy(Payment::getFeePlanId));

        int reminders = 0;
        int overdue = 0;
        for (FeePlan plan : plans) {
            FeeLedger ledger = FeeLedger.of(plan, payments.getOrDefault(plan.getId(), List.of()), today);
            for (FeeLedger.Line line : ledger.lines()) {
                if (!line.isOpen()) {
                    continue;
                }
                FeeInstallment installment = line.installment();
                long daysUntil = ChronoUnit.DAYS.between(today, installment.getDueDate());

                if (daysUntil < 0) {
                    if (installment.getOverdueNotifiedAt() == null) {
                        announceOverdue(plan, line);
                        installment.setOverdueNotifiedAt(Instant.now());
                        overdue++;
                    }
                    continue;
                }

                Integer step = reminderStep(daysUntil, installment.getLastReminderDays(), props.getDueReminderDays());
                if (step != null) {
                    remind(plan, line, daysUntil);
                    installment.setLastReminderDays(step);
                    reminders++;
                }
            }
        }
        planRepository.saveAll(plans);
        log.info("Fee scan: {} reminder(s), {} newly overdue installment(s)", reminders, overdue);
    }

    /**
     * Which reminder, if any, is due today.
     *
     * <p>The step is the smallest configured lead time that the due date is
     * now within, and it is sent only if a later (closer) step has not gone
     * out already. If the scan missed a few mornings, the student gets the one
     * reminder that fits now - not the 7-day, 3-day and 1-day messages at once.
     *
     * @return the step to record, or null when nothing should be sent
     */
    static Integer reminderStep(long daysUntil, Integer lastSent, Collection<Integer> steps) {
        Integer step = steps.stream()
                .filter(s -> s >= daysUntil)
                .min(Integer::compare)
                .orElse(null);
        if (step == null) {
            return null;
        }
        return lastSent == null || lastSent > step ? step : null;
    }

    private void remind(FeePlan plan, FeeLedger.Line line, long daysUntil) {
        if (plan.getStudentUserId() == null) {
            return;
        }
        String when = daysUntil == 0 ? "today" : daysUntil == 1 ? "tomorrow" : "in " + daysUntil + " days";
        events.notifyUsers(List.of(plan.getStudentUserId()), "FEE_DUE",
                "Fee installment due " + when,
                "Installment %d: %s %s due on %s.".formatted(line.installment().getInstallmentNo(),
                        props.getCurrencySymbol(), line.remaining().toPlainString(), line.installment().getDueDate()),
                "/student/fees");
    }

    private void announceOverdue(FeePlan plan, FeeLedger.Line line) {
        FeeInstallment installment = line.installment();
        events.publishAfterCommit(KafkaTopics.INSTALLMENT_OVERDUE, String.valueOf(plan.getStudentId()),
                new InstallmentOverdueEvent(DomainEvent.newId(), Instant.now(), plan.getId(), installment.getId(),
                        installment.getInstallmentNo(), plan.getStudentId(), plan.getStudentUserId(),
                        installment.getDueDate(), line.remaining()));

        String message = "Installment %d of %s %s was due on %s.".formatted(installment.getInstallmentNo(),
                props.getCurrencySymbol(), line.remaining().toPlainString(), installment.getDueDate());
        if (plan.getStudentUserId() != null) {
            events.notifyUsers(List.of(plan.getStudentUserId()), "FEE_OVERDUE", "Fee overdue", message,
                    "/student/fees");
        }
        events.notifyRole(Roles.FINANCE, "FEE_OVERDUE",
                "Overdue: " + (plan.getStudentName() == null ? "student " + plan.getStudentId() : plan.getStudentName()),
                message, "/finance/overdue");
    }
}
