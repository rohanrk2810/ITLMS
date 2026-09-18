package com.itilms.reporting.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.reporting.config.ReportingProperties;
import com.itilms.reporting.dto.DashboardSummaryResponse;
import com.itilms.reporting.repository.ActivityEventRepository;
import com.itilms.reporting.repository.StudentSessionAttendanceRepository;
import com.itilms.reporting.service.AttendancePercentage;
import com.itilms.reporting.service.DashboardService;

import lombok.RequiredArgsConstructor;

/**
 * Doc S15: dashboards are eventually consistent, built from
 * {@code activity_events} and {@code student_session_attendance}. Recomputing
 * on every request would mean every open dashboard tab re-runs the same eight
 * aggregate queries, so the result is cached in memory for
 * {@code dashboard-cache-seconds}.
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final ActivityEventRepository activityEvents;
    private final StudentSessionAttendanceRepository attendance;
    private final ReportingProperties props;

    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

    @Override
    public DashboardSummaryResponse summary() {
        long now = nowMillis();
        CacheEntry cached = cache.get();
        if (cached != null && now - cached.computedAtMillis < props.getDashboardCacheSeconds() * 1000L) {
            return cached.value;
        }
        DashboardSummaryResponse fresh = compute();
        cache.set(new CacheEntry(fresh, now));
        return fresh;
    }

    /** Package-visible so a test can control cache expiry without sleeping. */
    long nowMillis() {
        return System.currentTimeMillis();
    }

    @Transactional(readOnly = true)
    DashboardSummaryResponse compute() {
        LocalDate since = LocalDate.now(ZoneOffset.UTC).minusDays(30);

        long presentCount = attendance.countByStatus("PRESENT");
        long absentCount = attendance.countByStatus("ABSENT");
        long lateCount = attendance.countByStatus("LATE");
        long excusedCount = attendance.countByStatus("EXCUSED");
        AttendancePercentage attendancePct = new AttendancePercentage(presentCount, absentCount, lateCount, excusedCount);

        return new DashboardSummaryResponse(
                Instant.now(),
                activityEvents.countByMetricKey("STUDENT_ADMITTED"),
                activityEvents.countByMetricKeyAndEventDateGreaterThanEqual("STUDENT_ADMITTED", since),
                activityEvents.countByMetricKey("ENROLLMENT_CREATED"),
                activityEvents.countByMetricKeyAndEventDateGreaterThanEqual("ENROLLMENT_CREATED", since),
                nullToZero(activityEvents.sumAmountByMetricKey("PAYMENT_RECORDED")),
                nullToZero(activityEvents.sumAmountByMetricKeySince("PAYMENT_RECORDED", since)),
                nullToZero(activityEvents.sumAmountByMetricKey("FEE_PLAN_CREATED")),
                activityEvents.countByMetricKey("INSTALLMENT_OVERDUE"),
                nullToZero(activityEvents.sumAmountByMetricKey("INSTALLMENT_OVERDUE")),
                activityEvents.countByMetricKey("CERTIFICATE_ISSUED"),
                activityEvents.countByMetricKey("SUBMISSION_EVALUATED"),
                activityEvents.countByMetricKey("QUIZ_ATTEMPT_COMPLETED"),
                activityEvents.countByMetricKeyAndDimension("QUIZ_ATTEMPT_COMPLETED", "PASSED"),
                activityEvents.countByMetricKey("JOB_POSTED"),
                activityEvents.countByMetricKeyAndDimension("APPLICATION_STAGE_CHANGED", "SELECTED"),
                new DashboardSummaryResponse.AttendanceSnapshot(
                        presentCount, absentCount, lateCount, excusedCount, attendancePct.percent()));
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record CacheEntry(DashboardSummaryResponse value, long computedAtMillis) {
    }
}
