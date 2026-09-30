package com.itilms.liveclass.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.liveclass.dto.request.AnswerQuestionRequest;
import com.itilms.liveclass.dto.request.AskQuestionRequest;
import com.itilms.liveclass.dto.request.MuteRequest;
import com.itilms.liveclass.dto.response.LiveAnswerResponse;
import com.itilms.liveclass.dto.response.LiveQuestionResponse;
import com.itilms.liveclass.service.LiveQuestionService;
import com.itilms.liveclass.dto.request.ParticipantPermissionRequest;
import com.itilms.liveclass.dto.request.RoomPolicyRequest;
import com.itilms.liveclass.dto.response.JoinTokenResponse;
import com.itilms.liveclass.dto.response.LiveParticipantResponse;
import com.itilms.liveclass.dto.response.LiveSessionResponse;
import com.itilms.liveclass.dto.response.RoomControlsResponse;
import com.itilms.liveclass.dto.response.StudentParticipationResponse;
import com.itilms.liveclass.service.LiveClassService;
import com.itilms.liveclass.service.RoomControlService;
import com.itilms.liveclass.service.StudentParticipationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    private final RoomControlService controlService;
    private final LiveQuestionService questionService;
    private final com.itilms.liveclass.service.RecordingService recordingService;

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

    @Operation(summary = "My finished live classes",
            description = "Most recent first - where a recording is opened from, once it has one.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/past")
    public List<LiveSessionResponse> past() {
        return liveClassService.pastForCaller();
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

    @Operation(summary = "Who may use a microphone, camera or screen share in this class",
            description = "The room policy and, for everyone who has joined, what they may switch on now. "
                    + "The class trainer and staff only.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/sessions/{id}/controls")
    public RoomControlsResponse controls(@PathVariable Long id) {
        return controlService.controls(id);
    }

    @Operation(summary = "Set what students may switch on",
            description = "Applies at once to students in the room and to anyone who joins later. A host's override "
                    + "for one student still wins. Screen sharing is off for students unless turned on here.")
    @PreAuthorize(Roles.ACADEMIC)
    @PutMapping("/sessions/{id}/policy")
    public RoomControlsResponse updatePolicy(@PathVariable Long id, @RequestBody RoomPolicyRequest request) {
        return controlService.updatePolicy(id, request);
    }

    @Operation(summary = "Allow or deny one student's microphone, camera or screen share",
            description = "Overrides the room policy for that student; `followRoom` clears the overrides.")
    @PreAuthorize(Roles.ACADEMIC)
    @PutMapping("/sessions/{id}/participants/{userId}/permissions")
    public RoomControlsResponse updateParticipant(@PathVariable Long id, @PathVariable Long userId,
                                                  @RequestBody ParticipantPermissionRequest request) {
        return controlService.updateParticipant(id, userId, request);
    }

    @Operation(summary = "Switch off one student's microphone, camera or screen share",
            description = "They may switch it on again if the policy still allows it; deny it as well to keep it off.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/sessions/{id}/participants/{userId}/mute")
    public MuteResult mute(@PathVariable Long id, @PathVariable Long userId, @Valid @RequestBody MuteRequest request) {
        return new MuteResult(controlService.mute(id, userId, request));
    }

    @Operation(summary = "Mute every student's microphone",
            description = "Hosts' microphones are left alone. Students who are still permitted may unmute.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/sessions/{id}/mute-all")
    public MuteResult muteAll(@PathVariable Long id) {
        return new MuteResult(controlService.muteAll(id));
    }

    @Operation(summary = "Start recording the class",
            description = "The class trainer or staff only. One capture at a time; the class must be live.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/sessions/{id}/recording/start")
    public ResponseEntity<Void> startRecording(@PathVariable Long id) {
        recordingService.start(id);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Stop recording the class",
            description = "Finishing the file takes a little longer; the recording becomes available once it is ready.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/sessions/{id}/recording/stop")
    public ResponseEntity<Void> stopRecording(@PathVariable Long id) {
        recordingService.stop(id);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Watch a class's recording",
            description = "The class trainer/staff, or a student enrolled in the batch. 404 until one exists. "
                    + "Returns the whole file rather than honouring byte ranges, so the frontend fetches it once "
                    + "and scrubs within what it has downloaded.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/sessions/{id}/recording")
    public ResponseEntity<org.springframework.core.io.Resource> recording(@PathVariable Long id) throws java.io.IOException {
        java.io.File file = recordingService.fileFor(id);
        var resource = new org.springframework.core.io.FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.valueOf("video/mp4"))
                .contentLength(resource.contentLength())
                .body(resource);
    }

    @Operation(summary = "Ask the class a question",
            description = "MCQ, multiple select, true/false, short answer, coding or other. The question is stamped "
                    + "with how far into the class it was asked, and pushed to the students in the room. Asking a "
                    + "new question closes the previous one.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/sessions/{id}/questions")
    public LiveQuestionResponse ask(@PathVariable Long id, @Valid @RequestBody AskQuestionRequest request) {
        return questionService.ask(id, request);
    }

    @Operation(summary = "Close a question", description = "Students can still answer it later from the recording.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/questions/{questionId}/close")
    public LiveQuestionResponse closeQuestion(@PathVariable Long questionId) {
        return questionService.closeQuestion(questionId);
    }

    @Operation(summary = "Every student's answer to a question")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/questions/{questionId}/answers")
    public List<LiveAnswerResponse> answers(@PathVariable Long questionId) {
        return questionService.answersTo(questionId);
    }

    @Operation(summary = "A class's questions, in the order they were asked",
            description = "Each carries its offset from the class start (for a recording's timeline). Students see the "
                    + "answer key only once a question has closed or they have answered.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/class-sessions/{classSessionId}/questions")
    public List<LiveQuestionResponse> questions(@PathVariable Long classSessionId) {
        return questionService.forClass(classSessionId);
    }

    @Operation(summary = "The question being asked right now", description = "204 when there is none.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/class-sessions/{classSessionId}/questions/open")
    public ResponseEntity<LiveQuestionResponse> openQuestion(@PathVariable Long classSessionId) {
        LiveQuestionResponse open = questionService.openQuestion(classSessionId);
        return open == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(open);
    }

    @Operation(summary = "Answer a question", description = "Students only, once per question. After a question has "
            + "closed this is the recorded-class answer.")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/questions/{questionId}/answer")
    public LiveQuestionResponse answer(@PathVariable Long questionId, @Valid @RequestBody AnswerQuestionRequest request) {
        return questionService.answer(questionId, request);
    }

    /** How many tracks were switched off. */
    public record MuteResult(int muted) {
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
