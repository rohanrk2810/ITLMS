package com.itilms.batch.service;

import java.time.LocalDate;
import java.util.List;

import com.itilms.batch.dto.request.MarkAttendanceRequest;
import com.itilms.batch.dto.response.AttendanceResponse;
import com.itilms.batch.dto.response.AttendanceSummaryResponse;
import com.itilms.common.event.LiveAttendanceComputedEvent;

/** Attendance (Doc S6.9, S14). */
public interface AttendanceService {

    /**
     * Saves or corrects the register for a session.
     *
     * <p>A trainer may only mark their own batches. Re-marking an already-saved
     * register is a correction: it requires a reason and is audited, because Doc
     * S14 treats changing attendance after the fact as a privileged act.
     */
    List<AttendanceResponse> mark(Long sessionId, MarkAttendanceRequest request);

    List<AttendanceResponse> forSession(Long sessionId);

    /** A student's own attendance history (Doc S8.2). */
    List<AttendanceResponse> studentHistory(Long studentId, Long batchId, LocalDate from, LocalDate to);

    AttendanceSummaryResponse studentSummary(Long studentId, Long batchId);

    /** Every student's percentage in a batch, for the trainer's register view. */
    List<AttendanceSummaryResponse> batchSummary(Long batchId);

    /** Students below the institute's attendance threshold (Doc S15). */
    List<AttendanceSummaryResponse> attendanceAlerts(Long batchId);

    /**
     * Writes attendance worked out from time spent in a live room.
     *
     * <p>Never overwrites a mark a human has already made: once a trainer has
     * judged someone present, a later recomputation must not quietly reverse it.
     */
    void applyLiveAttendance(LiveAttendanceComputedEvent event);
}
