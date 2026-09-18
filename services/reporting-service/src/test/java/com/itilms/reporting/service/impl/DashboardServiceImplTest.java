package com.itilms.reporting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.itilms.reporting.config.ReportingProperties;
import com.itilms.reporting.dto.DashboardSummaryResponse;
import com.itilms.reporting.repository.ActivityEventRepository;
import com.itilms.reporting.repository.StudentSessionAttendanceRepository;

/** The in-memory cache in front of the eight dashboard aggregate queries (Doc S15). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceImplTest {

    @Mock private ActivityEventRepository activityEvents;
    @Mock private StudentSessionAttendanceRepository attendance;

    private ReportingProperties props;
    private long[] clockMillis;

    @BeforeEach
    void setUp() {
        props = new ReportingProperties();
        props.setDashboardCacheSeconds(60);
        clockMillis = new long[] {0L};

        when(activityEvents.countByMetricKey(anyString())).thenReturn(1L);
        when(activityEvents.countByMetricKeyAndEventDateGreaterThanEqual(anyString(), any())).thenReturn(1L);
        when(activityEvents.countByMetricKeyAndDimension(anyString(), anyString())).thenReturn(1L);
        when(activityEvents.sumAmountByMetricKey(anyString())).thenReturn(BigDecimal.TEN);
        when(activityEvents.sumAmountByMetricKeySince(anyString(), any())).thenReturn(BigDecimal.ONE);
        when(attendance.countByStatus(anyString())).thenReturn(5L);
    }

    private DashboardServiceImpl newService() {
        return new DashboardServiceImpl(activityEvents, attendance, props) {
            @Override
            long nowMillis() {
                return clockMillis[0];
            }
        };
    }

    @Test
    @DisplayName("A second call within the cache window does not touch the repositories again")
    void repeatedCallsWithinTheWindowAreCached() {
        DashboardServiceImpl service = newService();

        DashboardSummaryResponse first = service.summary();
        clockMillis[0] += 30_000;
        DashboardSummaryResponse second = service.summary();

        assertThat(second).isSameAs(first);
        verify(activityEvents, times(1)).countByMetricKey("STUDENT_ADMITTED");
    }

    @Test
    @DisplayName("A call past the cache window recomputes")
    void callAfterTheWindowRecomputes() {
        DashboardServiceImpl service = newService();

        service.summary();
        clockMillis[0] += 61_000;
        service.summary();

        verify(activityEvents, times(2)).countByMetricKey("STUDENT_ADMITTED");
    }

    @Test
    @DisplayName("A missing payment sum reads as zero, not null")
    void missingAmountSumsReadAsZero() {
        when(activityEvents.sumAmountByMetricKey(anyString())).thenReturn(null);
        DashboardSummaryResponse summary = newService().summary();
        assertThat(summary.revenueCollectedTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Attendance counts flow through into the snapshot")
    void attendanceCountsAppearInTheSnapshot() {
        when(attendance.countByStatus("PRESENT")).thenReturn(8L);
        when(attendance.countByStatus("ABSENT")).thenReturn(2L);
        when(attendance.countByStatus("LATE")).thenReturn(0L);
        when(attendance.countByStatus("EXCUSED")).thenReturn(0L);

        DashboardSummaryResponse summary = newService().summary();
        assertThat(summary.attendance().present()).isEqualTo(8L);
        assertThat(summary.attendance().percent()).isEqualByComparingTo(BigDecimal.valueOf(80));
    }
}
