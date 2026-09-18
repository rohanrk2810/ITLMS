package com.itilms.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R8 (docs/02): an excused absence sits outside the percentage entirely. */
class AttendancePercentageTest {

    @Test
    @DisplayName("All present is 100%")
    void allPresentIsFullMarks() {
        AttendancePercentage stats = new AttendancePercentage(10, 0, 0, 0);
        assertThat(stats.percent()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("Late counts as attended")
    void lateCountsAsAttended() {
        AttendancePercentage stats = new AttendancePercentage(8, 0, 2, 0);
        assertThat(stats.percent()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("An excused absence is left out of both sides of the fraction")
    void excusedIsExcludedEntirely() {
        // 8 present out of 8 counted sessions - the 2 excused never enter the denominator.
        AttendancePercentage stats = new AttendancePercentage(8, 0, 0, 2);
        assertThat(stats.percent()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(stats.countedSessions()).isEqualTo(8);
        assertThat(stats.totalSessions()).isEqualTo(10);
    }

    @Test
    @DisplayName("An unapproved absence does cost the percentage")
    void unapprovedAbsenceLowersThePercentage() {
        AttendancePercentage stats = new AttendancePercentage(6, 4, 0, 0);
        assertThat(stats.percent()).isEqualByComparingTo(BigDecimal.valueOf(60));
    }

    @Test
    @DisplayName("No counted sessions yet is zero, not a division error")
    void noSessionsIsZero() {
        AttendancePercentage stats = new AttendancePercentage(0, 0, 0, 3);
        assertThat(stats.percent()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
