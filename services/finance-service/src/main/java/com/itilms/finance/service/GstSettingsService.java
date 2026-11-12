package com.itilms.finance.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.security.SecurityUtils;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.GstSettings;
import com.itilms.finance.repository.GstSettingsRepository;

import lombok.RequiredArgsConstructor;

/**
 * The GST rate the administrator sets. Once saved it replaces the deployment setting. The rate applies from its
 * effective date: a payment, receipt or book entry dated earlier is treated as having no GST, so turning GST on today
 * does not change last year.
 */
@Service
@RequiredArgsConstructor
public class GstSettingsService {

    private static final String GSTIN_PATTERN = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";

    private final GstSettingsRepository repository;
    private final FinanceProperties props;
    private final EventPublisher events;
    private final BooksLockService booksLock;

    public record Settings(BigDecimal percent, String gstin, String sacCode, LocalDate effectiveFrom, boolean fromPortal,
                           Long setBy, Instant setAt) {
    }

    public record Update(BigDecimal percent, String gstin, String sacCode, LocalDate effectiveFrom) {
    }

    @Transactional(readOnly = true)
    public Settings current() {
        GstSettings row = repository.findById((short) 1).orElse(null);
        if (row != null && row.getSetAt() != null) {
            return new Settings(row.getPercent(), row.getGstin() == null ? "" : row.getGstin(),
                    row.getSacCode() == null ? "" : row.getSacCode(), row.getEffectiveFrom(), true, row.getSetBy(), row.getSetAt());
        }
        // Not saved in the portal yet: the deployment setting, from the beginning of time.
        return new Settings(props.getGstPercent() == null ? BigDecimal.ZERO : props.getGstPercent(), props.getGstin(),
                props.getSacCode(), null, false, null, null);
    }

    /** The rate that applies to something dated on this day: zero before the effective date. */
    @Transactional(readOnly = true)
    public BigDecimal rateFor(LocalDate date) {
        return rateFor(current(), date);
    }

    public static BigDecimal rateFor(Settings s, LocalDate date) {
        if (s.percent() == null || s.percent().signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (s.effectiveFrom() != null && date != null && date.isBefore(s.effectiveFrom())) {
            return BigDecimal.ZERO;
        }
        return s.percent();
    }

    @Transactional
    public Settings save(Update update) {
        BigDecimal percent = update.percent() == null ? BigDecimal.ZERO : update.percent();
        if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(28)) > 0) {
            throw new BusinessRuleException("The GST rate must be between 0 and 28 percent.");
        }
        String gstin = update.gstin() == null ? "" : update.gstin().trim().toUpperCase(java.util.Locale.ROOT);
        if (percent.signum() > 0 && !gstin.isEmpty() && !gstin.matches(GSTIN_PATTERN)) {
            throw new BusinessRuleException("The GSTIN must be 15 characters, like 27ABCDE1234F1Z5.");
        }
        String sac = update.sacCode() == null ? "" : update.sacCode().trim();
        if (!sac.isEmpty() && !sac.matches("^[0-9]{4,8}$")) {
            throw new BusinessRuleException("The SAC code is 4 to 8 digits, for example 999293.");
        }
        LocalDate from = update.effectiveFrom() != null ? update.effectiveFrom() : LocalDate.now(props.getZone());
        if (percent.signum() > 0) {
            // A rate that starts inside a closed period would change figures already given out.
            booksLock.requireOpen(from, "a GST change");
        }
        GstSettings row = repository.findById((short) 1).orElseGet(GstSettings::new);
        Settings before = current();
        row.setPercent(percent.setScale(2, java.math.RoundingMode.HALF_UP));
        row.setGstin(gstin.isEmpty() ? null : gstin);
        row.setSacCode(sac.isEmpty() ? null : sac);
        row.setEffectiveFrom(from);
        row.setSetBy(SecurityUtils.requirePrincipal().userId());
        row.setSetAt(Instant.now());
        repository.save(row);
        events.audit("finance-service", "GST_SETTINGS_CHANGED", "GstSettings", 1L,
                Map.of("percent", before.percent().toPlainString(), "from", String.valueOf(before.effectiveFrom())),
                Map.of("percent", row.getPercent().toPlainString(), "from", from.toString(),
                        "gstin", gstin, "sac", sac));
        return current();
    }
}
