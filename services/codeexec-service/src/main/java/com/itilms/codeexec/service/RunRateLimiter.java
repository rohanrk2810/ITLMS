package com.itilms.codeexec.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;

import com.itilms.codeexec.config.CodeExecProperties;
import com.itilms.codeexec.exception.RunLimitException;

/**
 * Caps how often one signed-in user may run code: a burst limit per minute and a total per day.
 *
 * <p>Fixed windows, in memory. The point is to keep one student's loop from using up the
 * sandbox, not to bill anyone, so the small unfairness at a window edge is fine. Counters
 * live in this process: with several codeexec instances the effective limit is per instance.
 */
public class RunRateLimiter {

    private final CodeExecProperties.RateLimit limit;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Usage> usage = new ConcurrentHashMap<>();

    public RunRateLimiter(CodeExecProperties.RateLimit limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    /** Counts one run for the user, or throws {@link RunLimitException} when they are over a limit. */
    public void acquire(long userId) {
        Instant now = clock.instant();
        long minute = now.getEpochSecond() / 60;
        LocalDate day = now.atZone(ZoneOffset.UTC).toLocalDate();

        usage.compute(userId, (id, current) -> {
            Usage next = current == null ? new Usage() : current;
            if (next.minute != minute) {
                next.minute = minute;
                next.minuteCount = 0;
            }
            if (!day.equals(next.day)) {
                next.day = day;
                next.dayCount = 0;
            }
            if (next.minuteCount >= limit.getRunsPerMinute()) {
                throw new RunLimitException("RUN_RATE_LIMIT",
                        "You are running code too fast. Wait a minute and try again.");
            }
            if (next.dayCount >= limit.getRunsPerDay()) {
                throw new RunLimitException("RUN_DAILY_LIMIT",
                        "You have used today's " + limit.getRunsPerDay() + " runs. The limit resets at midnight UTC.");
            }
            next.minuteCount++;
            next.dayCount++;
            return next;
        });
    }

    /** Drops users whose last run was before today, so the map cannot grow forever. */
    public void evictStale() {
        LocalDate today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
        usage.entrySet().removeIf(entry -> entry.getValue().day != null && entry.getValue().day.isBefore(today));
    }

    int trackedUsers() {
        return usage.size();
    }

    private static final class Usage {
        long minute = -1;
        int minuteCount;
        LocalDate day;
        int dayCount;
    }
}
