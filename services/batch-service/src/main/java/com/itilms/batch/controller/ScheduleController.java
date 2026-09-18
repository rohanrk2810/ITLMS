package com.itilms.batch.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.batch.dto.request.CreateSessionRequest;
import com.itilms.batch.dto.request.GenerateScheduleRequest;
import com.itilms.batch.dto.response.SessionResponse;
import com.itilms.batch.service.ScheduleService;
import com.itilms.common.security.Roles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The timetable (Doc S6.7, S11). */
@Tag(name = "Timetable", description = "Class sessions and scheduling")
@RestController
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(summary = "My timetable",
            description = "Scoped automatically: students see their own batches, trainers see "
                    + "theirs, staff see everything. There is no batch id a student can pass "
                    + "to view another batch's schedule.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/timetable")
    public List<SessionResponse> timetable(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long batchId) {
        return scheduleService.timetableForCaller(from, to, batchId);
    }

    @Operation(summary = "Today's classes", description = "The dashboard's first widget (Doc S15).")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/timetable/today")
    public List<SessionResponse> today() {
        return scheduleService.today();
    }

    @Operation(summary = "Sessions still awaiting attendance",
            description = "Finished classes whose register was never filled in.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/api/timetable/pending-attendance")
    public List<SessionResponse> pendingAttendance(
            @RequestParam(defaultValue = "14") int lookbackDays) {
        return scheduleService.pendingAttendance(lookbackDays);
    }

    @Operation(summary = "A batch's full schedule")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/batches/{batchId}/sessions")
    public List<SessionResponse> forBatch(@PathVariable Long batchId) {
        return scheduleService.sessionsForBatch(batchId);
    }

    @Operation(summary = "Get one session")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/sessions/{id}")
    public SessionResponse get(@PathVariable Long id) {
        return scheduleService.get(id);
    }

    @Operation(summary = "Schedule a session")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/api/sessions")
    public ResponseEntity<SessionResponse> create(@Valid @RequestBody CreateSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.createSession(request));
    }

    @Operation(summary = "Update or reschedule a session",
            description = "Moving a session notifies the batch and the trainer (Doc S16).")
    @PreAuthorize(Roles.ACADEMIC)
    @PutMapping("/api/sessions/{id}")
    public SessionResponse update(@PathVariable Long id,
                                  @Valid @RequestBody CreateSessionRequest request) {
        return scheduleService.updateSession(id, request);
    }

    @Operation(summary = "Cancel a session",
            description = "Refused once attendance has been recorded for it.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/api/sessions/{id}/cancel")
    public SessionResponse cancel(@PathVariable Long id,
                                  @RequestParam(required = false) String reason) {
        return scheduleService.cancelSession(id, reason);
    }

    @Operation(summary = "Generate a batch's timetable",
            description = "Expands the batch's weekly pattern across a date range, skipping "
                    + "holidays and slots that already exist. Safe to run more than once.")
    @PreAuthorize(Roles.STAFF)
    @PostMapping("/api/batches/{batchId}/sessions/generate")
    public ResponseEntity<List<SessionResponse>> generate(
            @PathVariable Long batchId,
            @Valid @RequestBody GenerateScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduleService.generateSchedule(batchId, request));
    }
}
