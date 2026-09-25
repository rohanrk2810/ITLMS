package com.itilms.codeexec.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.itilms.codeexec.config.CodeExecProperties;
import com.itilms.codeexec.exception.RunLimitException;

class RunRateLimiterTest {

    private MutableClock clock;
    private RunRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-24T10:00:00Z"));
        CodeExecProperties.RateLimit limit = new CodeExecProperties.RateLimit();
        limit.setRunsPerMinute(3);
        limit.setRunsPerDay(5);
        limiter = new RunRateLimiter(limit, clock);
    }

    @Test
    void aUserMayRunUpToTheBurstLimitAndNotOneMoreInTheSameMinute() {
        limiter.acquire(1);
        limiter.acquire(1);
        limiter.acquire(1);

        assertThatThrownBy(() -> limiter.acquire(1))
                .isInstanceOfSatisfying(RunLimitException.class, e -> assertThat(e.getCode()).isEqualTo("RUN_RATE_LIMIT"));
    }

    @Test
    void theBurstLimitResetsWhenTheMinuteRollsOver() {
        limiter.acquire(1);
        limiter.acquire(1);
        limiter.acquire(1);

        clock.advance(Duration.ofSeconds(61));

        assertThatCode(() -> limiter.acquire(1)).doesNotThrowAnyException();
    }

    @Test
    void oneUsersRunsDoNotUseUpAnothersAllowance() {
        limiter.acquire(1);
        limiter.acquire(1);
        limiter.acquire(1);

        assertThatCode(() -> limiter.acquire(2)).doesNotThrowAnyException();
    }

    @Test
    void theDailyTotalHoldsEvenWhenEachMinuteIsWithinItsBurstLimit() {
        for (int i = 0; i < 5; i++) {
            limiter.acquire(1);
            clock.advance(Duration.ofMinutes(2));
        }

        assertThatThrownBy(() -> limiter.acquire(1))
                .isInstanceOfSatisfying(RunLimitException.class, e -> assertThat(e.getCode()).isEqualTo("RUN_DAILY_LIMIT"));
    }

    @Test
    void theDailyTotalResetsAtMidnightUtc() {
        for (int i = 0; i < 5; i++) {
            limiter.acquire(1);
            clock.advance(Duration.ofMinutes(2));
        }

        clock.set(Instant.parse("2026-09-25T00:00:05Z"));

        assertThatCode(() -> limiter.acquire(1)).doesNotThrowAnyException();
    }

    @Test
    void aRefusedRunIsNotCounted() {
        limiter.acquire(1);
        limiter.acquire(1);
        limiter.acquire(1);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> limiter.acquire(1)).isInstanceOf(RunLimitException.class);
        }

        clock.advance(Duration.ofSeconds(61));

        // 3 counted so far today; the 4 refusals must not have eaten into the 5-run day.
        limiter.acquire(1);
        limiter.acquire(1);
        assertThatThrownBy(() -> limiter.acquire(1))
                .isInstanceOfSatisfying(RunLimitException.class, e -> assertThat(e.getCode()).isEqualTo("RUN_DAILY_LIMIT"));
    }

    @Test
    void staleUsersAreEvictedOnceTheirDayIsOver() {
        limiter.acquire(1);
        limiter.acquire(2);

        limiter.evictStale();
        assertThat(limiter.trackedUsers()).isEqualTo(2);

        clock.set(Instant.parse("2026-09-25T00:00:05Z"));
        limiter.evictStale();
        assertThat(limiter.trackedUsers()).isZero();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
