package com.itilms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.BooksLock;
import com.itilms.finance.repository.BooksLockRepository;

class BooksLockServiceTest {

    private final BooksLockRepository repository = mock(BooksLockRepository.class);
    private final BooksLockService service = new BooksLockService(repository, new FinanceProperties(), mock(EventPublisher.class));
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    private final BooksLock lock = new BooksLock();

    @BeforeEach
    void setUp() {
        when(repository.findById((short) 1)).thenReturn(Optional.of(lock));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AppPrincipal admin = new AppPrincipal(1L, "a@x", "A", "ADMIN", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.authorities()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anOpenBookAcceptsAnyDate() {
        assertThatCode(() -> service.requireOpen(LocalDate.of(2020, 1, 1), "a payment")).doesNotThrowAnyException();
    }

    @Test
    void aClosedPeriodRefusesAChangeInsideItButNotAfter() {
        lock.setLockedThrough(LocalDate.of(2026, 3, 31));

        assertThatThrownBy(() -> service.requireOpen(LocalDate.of(2026, 3, 31), "a payment"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("closed up to 2026-03-31");
        assertThatCode(() -> service.requireOpen(LocalDate.of(2026, 4, 1), "a payment")).doesNotThrowAnyException();
    }

    @Test
    void todayCanNeverBeClosed() {
        assertThatThrownBy(() -> service.set(today, null)).isInstanceOf(BusinessRuleException.class);
        assertThat(service.set(today.minusDays(1), "March done").lockedThrough()).isEqualTo(today.minusDays(1));
    }

    @Test
    void reopeningNeedsAReason() {
        lock.setLockedThrough(today.minusDays(10));

        assertThatThrownBy(() -> service.set(today.minusDays(20), " ")).isInstanceOf(BusinessRuleException.class);
        assertThat(service.set(today.minusDays(20), "A missed cheque").lockedThrough()).isEqualTo(today.minusDays(20));
    }
}
