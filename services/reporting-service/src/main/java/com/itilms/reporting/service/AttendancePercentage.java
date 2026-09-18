package com.itilms.reporting.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Turns raw status counts into the dashboard's attendance percentage.
 *
 * <p>R8 in docs/02-documentation-review.md: an approved absence
 * ({@code EXCUSED}) is left out of the calculation entirely, on both sides of
 * the fraction, so it does not cost a student their percentage the way an
 * unapproved absence does. {@code LATE} counts as attended - this is
 * reporting-service's own dashboard view; batch-service remains the
 * authoritative record.
 */
public record AttendancePercentage(long present, long absent, long late, long excused) {

    public long countedSessions() {
        return present + absent + late;
    }

    public long totalSessions() {
        return countedSessions() + excused;
    }

    public BigDecimal percent() {
        long counted = countedSessions();
        if (counted == 0) {
            return BigDecimal.ZERO;
        }
        long attended = present + late;
        return BigDecimal.valueOf(attended)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(counted), 2, RoundingMode.HALF_UP);
    }
}
