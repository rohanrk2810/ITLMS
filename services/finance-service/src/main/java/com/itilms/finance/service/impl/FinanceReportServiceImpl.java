package com.itilms.finance.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.dto.response.FinanceDashboardResponse;
import com.itilms.finance.dto.response.OverdueItemResponse;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.FeePlanStatus;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.ledger.FeeLedger;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.service.FinanceReportService;

import lombok.RequiredArgsConstructor;

/**
 * The finance dashboard and the overdue list (Doc S15).
 *
 * <p>Overdue figures are worked out by running the same {@link FeeLedger} the
 * student's own fee page uses over every active plan. That costs one pass over
 * the active plans rather than a clever query, and it means the dashboard's
 * overdue total and the sum of what individual students see can never differ.
 */
@Service
@RequiredArgsConstructor
public class FinanceReportServiceImpl implements FinanceReportService {

    private final FeePlanRepository planRepository;
    private final PaymentRepository paymentRepository;
    private final FinanceProperties props;

    @Override
    @Transactional(readOnly = true)
    public FinanceDashboardResponse dashboard() {
        LocalDate today = LocalDate.now(props.getZone());

        BigDecimal billed = planRepository.totalBilled();
        BigDecimal collected = paymentRepository.totalCollected();

        BigDecimal overdueAmount = BigDecimal.ZERO;
        int overdueInstallments = 0;
        Set<Long> studentsOverdue = new HashSet<>();
        for (Map.Entry<FeePlan, FeeLedger> entry : activeLedgers(today).entrySet()) {
            FeeLedger ledger = entry.getValue();
            long overdueLines = ledger.lines().stream()
                    .filter(l -> l.state() == FeeLedger.InstallmentState.OVERDUE).count();
            if (overdueLines > 0) {
                overdueInstallments += (int) overdueLines;
                overdueAmount = overdueAmount.add(ledger.overdueAmount());
                studentsOverdue.add(entry.getKey().getStudentId());
            }
        }

        LocalDate monthStart = today.withDayOfMonth(1);
        List<FinanceDashboardResponse.MethodTotal> split = new ArrayList<>();
        BigDecimal thisMonth = BigDecimal.ZERO;
        for (Object[] row : paymentRepository.methodSplit(monthStart, today)) {
            BigDecimal amount = (BigDecimal) row[2];
            split.add(new FinanceDashboardResponse.MethodTotal(row[0].toString(), ((Number) row[1]).longValue(), amount));
            thisMonth = thisMonth.add(amount);
        }
        split.sort(Comparator.comparing(FinanceDashboardResponse.MethodTotal::amount).reversed());

        return new FinanceDashboardResponse(
                props.getCurrency(), billed, collected, billed.subtract(collected).max(BigDecimal.ZERO),
                overdueAmount, overdueInstallments, studentsOverdue.size(),
                planRepository.countByStatus(FeePlanStatus.ACTIVE),
                planRepository.countByStatus(FeePlanStatus.SETTLED),
                thisMonth, split);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OverdueItemResponse> overdue() {
        LocalDate today = LocalDate.now(props.getZone());
        List<OverdueItemResponse> items = new ArrayList<>();

        activeLedgers(today).forEach((plan, ledger) -> ledger.lines().stream()
                .filter(l -> l.state() == FeeLedger.InstallmentState.OVERDUE)
                .forEach(l -> items.add(new OverdueItemResponse(
                        plan.getId(), l.installment().getId(), plan.getStudentId(),
                        plan.getStudentCode(), plan.getStudentName(), l.installment().getInstallmentNo(),
                        l.installment().getDueDate(),
                        ChronoUnit.DAYS.between(l.installment().getDueDate(), today),
                        l.remaining()))));

        items.sort(Comparator.comparing(OverdueItemResponse::daysOverdue).reversed());
        return items;
    }

    private Map<FeePlan, FeeLedger> activeLedgers(LocalDate today) {
        List<FeePlan> plans = planRepository.findByStatus(FeePlanStatus.ACTIVE);
        if (plans.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Payment>> payments = paymentRepository
                .findByFeePlanIdIn(plans.stream().map(FeePlan::getId).toList()).stream()
                .collect(Collectors.groupingBy(Payment::getFeePlanId));
        return plans.stream().collect(Collectors.toMap(p -> p,
                p -> FeeLedger.of(p, payments.getOrDefault(p.getId(), List.of()), today)));
    }
}
