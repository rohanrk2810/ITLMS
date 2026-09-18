package com.itilms.identity.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.identity.repository.PasswordResetTokenRepository;
import com.itilms.identity.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deletes tokens that can no longer be used.
 *
 * <p>Every sign-in on every device writes a refresh token row. Without pruning,
 * a few hundred students over a few years turns into hundreds of thousands of
 * dead rows, and every token refresh pays for them in the index.
 *
 * <p>Expired rows are kept for a grace period rather than deleted the instant
 * they lapse, so that the replay detection in {@code AuthServiceImpl.refresh}
 * still has something to recognise when a stolen token is presented shortly
 * after expiry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupJob {

    private static final Duration GRACE_PERIOD = Duration.ofDays(30);

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /** Nightly, at a quiet hour for an institute. */
    @Scheduled(cron = "${itilms.security.token-cleanup-cron:0 15 3 * * *}")
    @Transactional
    public void purgeExpiredTokens() {
        Instant cutoff = Instant.now().minus(GRACE_PERIOD);

        int refreshDeleted = refreshTokenRepository.deleteExpiredBefore(cutoff);
        int resetDeleted = passwordResetTokenRepository.deleteExpiredBefore(cutoff);

        if (refreshDeleted > 0 || resetDeleted > 0) {
            log.info("Token cleanup removed {} refresh token(s) and {} reset token(s) older than {}",
                    refreshDeleted, resetDeleted, cutoff);
        }
    }
}
