package com.itilms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.CashClosing;
import com.itilms.finance.repository.CashClosingRepository;
import com.itilms.finance.repository.PaymentRepository;

class CashClosingServiceTest {

    private final CashClosingRepository closings = mock(CashClosingRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final CashClosingService service =
            new CashClosingService(closings, payments, new FinanceProperties(), mock(EventPublisher.class));
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    private void cashRecorded(String amount) {
        when(payments.cashReceivedOn(today)).thenReturn(new BigDecimal(amount));
        when(closings.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void aMatchingCountClosesTheDay() {
        cashRecorded("5000");

        var closing = service.close(today, new BigDecimal("5000"), null);

        assertThat(closing.difference()).isEqualByComparingTo("0");
    }

    @Test
    void aShortCountNeedsANote() {
        cashRecorded("5000");

        assertThatThrownBy(() -> service.close(today, new BigDecimal("4900"), " "))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("difference");
        assertThat(service.close(today, new BigDecimal("4900"), "Tea for the trainers").difference())
                .isEqualByComparingTo("-100");
    }

    @Test
    void aDayIsClosedOnce() {
        cashRecorded("5000");
        when(closings.findByClosingDate(today)).thenReturn(Optional.of(new CashClosing()));

        assertThatThrownBy(() -> service.close(today, new BigDecimal("5000"), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already");
    }

    @Test
    void aFutureDayCannotBeClosed() {
        assertThatThrownBy(() -> service.close(today.plusDays(1), BigDecimal.ZERO, null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
