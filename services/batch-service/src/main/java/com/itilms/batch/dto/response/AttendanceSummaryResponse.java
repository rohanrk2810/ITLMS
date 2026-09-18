package com.itilms.batch.dto.response;

import java.math.BigDecimal;
import java.math.RoundingMode;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A student's attendance percentage (Doc S6.9, S15).
 *
 * <p>EXCUSED sessions are excluded from {@code totalSessions} rather than
 * counted as absences, so an approved absence does not quietly cost a student
 * their certificate eligibility.
 */
@Schema(description = "Attendance percentage for one student")
public record AttendanceSummaryResponse(
        Long studentId,
        String studentName,
        Long batchId,
        int attendedSessions,
        @Schema(description = "Sessions counted, excluding cancelled classes and excused absences")
        int totalSessions,
        BigDecimal attendancePercent,
        @Schema(description = "True when the percentage is below the institute's threshold")
        boolean belowThreshold
) {

    public static AttendanceSummaryResponse of(Long studentId, String studentName, Long batchId,
                                               int attended, int total, double threshold) {
        BigDecimal percent = total <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(attended)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        // With no sessions held yet, a student is not "below threshold" - there
        // is simply nothing to judge. Reporting 0% as a failure on day one would
        // put every new student on the at-risk list.
        boolean below = total > 0 && percent.doubleValue() < threshold;

        return new AttendanceSummaryResponse(studentId, studentName, batchId,
                attended, total, percent, below);
    }
}
