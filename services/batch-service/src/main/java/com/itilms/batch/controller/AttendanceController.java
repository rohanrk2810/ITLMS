package com.itilms.batch.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.batch.dto.request.MarkAttendanceRequest;
import com.itilms.batch.dto.response.AttendanceResponse;
import com.itilms.batch.dto.response.AttendanceSummaryResponse;
import com.itilms.batch.service.AttendanceService;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.Roles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Attendance (Doc S6.9, S11). */
@Tag(name = "Attendance", description = "Marking and reporting attendance")
@RestController
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Operation(summary = "Save a session's register",
            description = "A trainer may only mark their own batches. Re-marking a saved register "
                    + "is a correction and requires correctionReason (Doc S14).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saved"),
            @ApiResponse(responseCode = "403", description = "The batch is not assigned to you"),
            @ApiResponse(responseCode = "422", description = "Correction without a reason, "
                    + "or a student who is not on the register")
    })
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/api/sessions/{sessionId}/attendance")
    public List<AttendanceResponse> mark(@PathVariable Long sessionId,
                                         @Valid @RequestBody MarkAttendanceRequest request) {
        return attendanceService.mark(sessionId, request);
    }

    @Operation(summary = "A session's register")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/sessions/{sessionId}/attendance")
    public List<AttendanceResponse> forSession(@PathVariable Long sessionId) {
        return attendanceService.forSession(sessionId);
    }

    @Operation(summary = "My attendance", description = "The signed-in student's own history.")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/api/students/me/attendance")
    public List<AttendanceResponse> myAttendance(
            @AuthenticationPrincipal AppPrincipal principal,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireProfile(principal);
        return attendanceService.studentHistory(principal.profileId(), batchId, from, to);
    }

    @Operation(summary = "My attendance percentage")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/api/students/me/attendance/summary")
    public AttendanceSummaryResponse mySummary(@AuthenticationPrincipal AppPrincipal principal,
                                               @RequestParam Long batchId) {
        requireProfile(principal);
        return attendanceService.studentSummary(principal.profileId(), batchId);
    }

    @Operation(summary = "A student's attendance percentage",
            description = "Staff view. Also used by certificate-service to check the "
                    + "attendance condition from Doc S7.3.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/attendance/students/{studentId}")
    public AttendanceSummaryResponse studentSummary(@PathVariable Long studentId,
                                                    @RequestParam Long batchId) {
        return attendanceService.studentSummary(studentId, batchId);
    }

    @Operation(summary = "Attendance across a batch",
            description = "Every student's percentage, lowest first.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/api/attendance/batches/{batchId}")
    public List<AttendanceSummaryResponse> batchSummary(@PathVariable Long batchId) {
        return attendanceService.batchSummary(batchId);
    }

    @Operation(summary = "Attendance alerts",
            description = "Students below the institute's threshold — the trainer dashboard widget.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/api/attendance/batches/{batchId}/alerts")
    public List<AttendanceSummaryResponse> alerts(@PathVariable Long batchId) {
        return attendanceService.attendanceAlerts(batchId);
    }

    private void requireProfile(AppPrincipal principal) {
        if (principal.profileId() == null) {
            throw new ForbiddenOperationException(
                    "Your account is not linked to a student profile yet.");
        }
    }
}
