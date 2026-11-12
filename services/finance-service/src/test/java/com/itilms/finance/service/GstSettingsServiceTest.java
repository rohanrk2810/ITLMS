package com.itilms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import com.itilms.finance.entity.GstSettings;
import com.itilms.finance.repository.GstSettingsRepository;

class GstSettingsServiceTest {

    private final GstSettingsRepository repository = mock(GstSettingsRepository.class);
    private final FinanceProperties props = new FinanceProperties();
    private final GstSettingsService service =
            new GstSettingsService(repository, props, mock(EventPublisher.class), mock(BooksLockService.class));
    private final GstSettings row = new GstSettings();

    @BeforeEach
    void setUp() {
        when(repository.findById((short) 1)).thenReturn(Optional.of(row));
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
    void usesTheDeploymentSettingUntilTheAdministratorSavesOne() {
        props.setGstPercent(new BigDecimal("12"));

        assertThat(service.current().percent()).isEqualByComparingTo("12");
        assertThat(service.current().fromPortal()).isFalse();
    }

    @Test
    void savedSettingsReplaceTheDeploymentOnesAndApplyFromTheirDate() {
        props.setGstPercent(new BigDecimal("12"));

        var saved = service.save(new GstSettingsService.Update(new BigDecimal("18"), "27ABCDE1234F1Z5", "999293",
                LocalDate.of(2026, 4, 1)));

        assertThat(saved.percent()).isEqualByComparingTo("18");
        assertThat(saved.fromPortal()).isTrue();
        assertThat(service.rateFor(LocalDate.of(2026, 3, 31))).isEqualByComparingTo("0");
        assertThat(service.rateFor(LocalDate.of(2026, 4, 1))).isEqualByComparingTo("18");
    }

    @Test
    void refusesARateAboveTwentyEightAndABadGstin() {
        assertThatThrownBy(() -> service.save(new GstSettingsService.Update(new BigDecimal("30"), "", "", null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.save(new GstSettingsService.Update(new BigDecimal("18"), "NOTAGSTIN", "", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("GSTIN");
    }

    @Test
    void zeroSwitchesGstOff() {
        service.save(new GstSettingsService.Update(new BigDecimal("18"), "", "", LocalDate.of(2026, 4, 1)));
        service.save(new GstSettingsService.Update(BigDecimal.ZERO, "", "", LocalDate.of(2026, 5, 1)));

        assertThat(service.rateFor(LocalDate.of(2026, 6, 1))).isEqualByComparingTo("0");
    }
}
