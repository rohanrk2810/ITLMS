package com.itilms.finance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.CashClosing;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.repository.CashClosingRepository;
import com.itilms.finance.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/** The day-end cash count: compare the drawer with the cash the system recorded, and keep the result. */
@Service
@RequiredArgsConstructor
public class CashClosingService {

    private final CashClosingRepository closings;
    private final PaymentRepository payments;
    private final FinanceProperties props;
    private final EventPublisher events;
    private final com.itilms.finance.repository.RefundRepository refunds;
    private final BooksLockService booksLock;

    public record Preview(LocalDate date, BigDecimal expectedCash, long cashPayments, Closing closing) {
    }

    public record Closing(Long id, LocalDate date, BigDecimal expectedCash, BigDecimal countedCash, BigDecimal difference,
                          String note, Long closedBy) {
        static Closing from(CashClosing c) {
            return new Closing(c.getId(), c.getClosingDate(), c.getExpectedCash(), c.getCountedCash(), c.getDifference(),
                    c.getNote(), c.getCreatedBy());
        }
    }

    @Transactional(readOnly = true)
    public Preview preview(LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(props.getZone());
        return new Preview(day, expectedOn(day), payments.cashPaymentsOn(day),
                closings.findByClosingDate(day).map(Closing::from).orElse(null));
    }

    /** The cash the drawer should hold for a day: cash received, less cash handed back in refunds. */
    private BigDecimal expectedOn(LocalDate day) {
        return payments.cashReceivedOn(day).subtract(refunds.cashPaidOutOn(day));
    }

    @Transactional(readOnly = true)
    public List<Closing> recent() {
        return closings.findTop30ByOrderByClosingDateDesc().stream().map(Closing::from).toList();
    }

    @Transactional
    public Closing close(LocalDate date, BigDecimal countedCash, String note) {
        LocalDate today = LocalDate.now(props.getZone());
        if (date.isAfter(today)) {
            throw new BusinessRuleException("A day that has not happened yet cannot be closed.");
        }
        booksLock.requireOpen(date, "a cash count");
        if (closings.findByClosingDate(date).isPresent()) {
            throw new BusinessRuleException("This day has already been closed.");
        }
        BigDecimal expected = FeePlan.money(expectedOn(date));
        BigDecimal counted = FeePlan.money(countedCash);
        BigDecimal difference = counted.subtract(expected);
        if (difference.signum() != 0 && (note == null || note.isBlank())) {
            throw new BusinessRuleException("The count differs from the system by %s %s. Write what the difference is."
                    .formatted(props.getCurrencySymbol(), difference.toPlainString()));
        }
        CashClosing saved = closings.save(CashClosing.builder().closingDate(date).expectedCash(expected)
                .countedCash(counted).difference(difference).note(note == null || note.isBlank() ? null : note.trim())
                .build());
        events.audit("finance-service", "CASH_DAY_CLOSED", "CashClosing", saved.getId(), null,
                Map.of("date", date.toString(), "expected", expected.toPlainString(), "counted", counted.toPlainString(),
                        "difference", difference.toPlainString()));
        return Closing.from(saved);
    }
}
