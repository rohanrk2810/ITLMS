package com.itilms.liveclass.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.liveclass.dto.response.JoinTokenResponse;
import com.itilms.liveclass.dto.response.LiveParticipantResponse;
import com.itilms.liveclass.dto.response.LiveSessionResponse;
import com.itilms.liveclass.dto.response.StudentParticipationResponse;
import com.itilms.liveclass.service.LiveClassService;
import com.itilms.liveclass.service.StudentParticipationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Live classes.
 *
 * <p>A live class is addressed by the timetable's session id wherever a person
 * is the caller - the student clicks Join on a timetable entry and has no reason
 * to know a second id exists. The live-session id appears only on the trainer and
 * staff views that are about the room itself.
 */
@Tag(name = "Live classes", description = "Joining, running and reviewing online classes")
@RestController
@RequestMapping("/api/liveclass")
@RequiredArgsConstructor
public class LiveClassController {

    private final LiveClassService liveClassService;
    private final StudentParticipationService participationService;

    @Operation(summary = "Internal: how often one student joined their batches' live classes",
            description = "For reporting-service's student progress report. Not reachable through the gateway; "
                    + "reporting-service decides who may see which student.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/internal/students/{studentId}/participation")
    public StudentParticipationResponse participation(@PathVariable Long studentId,
                                                      @RequestParam(defaultValue = "") List<Long> batchIds) {
        return participationService.of(studentId, batchIds);
    }

    @Operation(summary = "Join a live class",
            description = "Returns a short-lived LiveKit token. Students must be enrolled in the batch; "
                    + "trainers must teach it; staff may observe any class. The room opens shortly "
                    + "before the scheduled start.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token issued"),
            @ApiResponse(responseCode = "403", description = "Not enrolled in, or not teaching, this batch"),
            @ApiResponse(responseCode = "422", description = "Class cancelled, finished, not online, "
                    + "or not open yet"),
            @ApiResponse(responseCode = "503", description = "Timetable or media server unreachable")
    })
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/class-sessions/{classSessionId}/join")
    public JoinTokenResponse join(@PathVariable Long classSessionId) {
        return liveClassService.join(classSessionId);
    }

    @Operation(summary = "Live room status for a timetable session",
            description = "Whether the room exists, is live, and can be joined right now.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/class-sessions/{classSessionId}")
    public LiveSessionResponse byClassSession(@PathVariable Long classSessionId) {
        return liveClassService.byClassSession(classSessionId);
    }

    @Operation(summary = "My upcoming live classes",
            description = "Students and trainers see their own batches; staff see today's classes.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/upcoming")
    public List<LiveSessionResponse> upcoming() {
        return liveClassService.upcomingForCaller();
    }

    @Operation(summary = "My time in live classes",
            description = "The room time behind your automatic attendance, class by class.")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me/attendance")
    public List<LiveParticipantResponse> myRoomTime(@RequestParam Long batchId) {
        return liveClassService.myRoomTime(batchId);
    }

    @Operation(summary = "A live class with its participants",
            description = "Who joined, when, for how long, and the computed verdict.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/sessions/{id}")
    public LiveSessionResponse get(@PathVariable Long id) {
        return liveClassService.get(id);
    }

    @Operation(summary = "A batch's live classes")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/batches/{batchId}")
    public List<LiveSessionResponse> forBatch(@PathVariable Long batchId) {
        return liveClassService.forBatch(batchId);
    }

    @Operation(summary = "End the class",
            description = "Closes the room for everyone and records attendance immediately, instead of "
                    + "waiting for the room to empty.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/sessions/{id}/end")
    public LiveSessionResponse end(@PathVariable Long id) {
        return liveClassService.endClass(id);
    }

    @Operation(summary = "Remove a student from the class",
            description = "Their time up to removal still counts. They can rejoin unless they are "
                    + "also dropped from the batch.")
    @PreAuthorize(Roles.ACADEMIC)
    @DeleteMapping("/sessions/{id}/participants/{userId}")
    public ResponseEntity<Void> removeParticipant(@PathVariable Long id, @PathVariable Long userId) {
        liveClassService.removeParticipant(id, userId);
        return ResponseEntity.noContent().build();
    }
}
