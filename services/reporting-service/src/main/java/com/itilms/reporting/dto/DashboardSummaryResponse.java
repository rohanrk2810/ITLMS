package com.itilms.reporting.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Institute-wide counters (Doc S15), built from events and cached for {@code dashboard-cache-seconds}. */
public record DashboardSummaryResponse(
        Instant generatedAt,
        long studentsAdmittedTotal,
        long studentsAdmittedLast30Days,
        long enrollmentsTotal,
        long enrollmentsLast30Days,
        BigDecimal revenueCollectedTotal,
        BigDecimal revenueCollectedLast30Days,
        BigDecimal feesBilledTotal,
        long installmentsOverdueTotal,
        BigDecimal overdueAmountTotal,
        long certificatesIssuedTotal,
        long submissionsEvaluatedTotal,
        long quizAttemptsTotal,
        long quizAttemptsPassed,
        long jobsPostedTotal,
        long placementsSelectedTotal,
        AttendanceSnapshot attendance
) {
    public record AttendanceSnapshot(long present, long absent, long late, long excused, BigDecimal percent) {
    }
}
