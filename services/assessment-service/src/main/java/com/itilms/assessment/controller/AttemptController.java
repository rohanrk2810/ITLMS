package com.itilms.assessment.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.assessment.dto.request.SubmitAttemptRequest;
import com.itilms.assessment.dto.request.ViolationRequest;
import com.itilms.assessment.dto.response.AnswerSaveResponse;
import com.itilms.assessment.dto.response.AttemptResultResponse;
import com.itilms.assessment.dto.response.AttemptViewResponse;
import com.itilms.assessment.dto.response.CodingRunResponse;
import com.itilms.assessment.dto.response.ViolationResponse;
import com.itilms.assessment.service.AttemptService;
import com.itilms.common.security.Roles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Sitting a test (Doc S6.11, S11). */
@Tag(name = "Test attempts", description = "Answering, submitting and reading results")
@RestController
@RequestMapping("/api/quiz-attempts")
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attemptService;
    private final com.itilms.assessment.service.CodingComparisonService comparisons;

    @Operation(summary = "The paper for a running attempt", description = "Never includes the answer key.")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/{id}")
    public AttemptViewResponse paper(@PathVariable Long id) {
        return attemptService.paper(id);
    }

    @Operation(summary = "Save answers so far",
            description = "What is saved is what gets scored if time runs out, so save as you go.")
    @PreAuthorize("hasRole('STUDENT')")
    @PutMapping("/{id}/answers")
    public AnswerSaveResponse save(@PathVariable Long id, @Valid @RequestBody SubmitAttemptRequest request) {
        return attemptService.saveAnswers(id, request);
    }

    @Operation(summary = "Run a coding question's tests",
            description = "Runs the code against every test case and remembers the result. Hidden cases show only pass or fail. "
                    + "429 when running too often; the code is kept either way.")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/questions/{questionId}/run-tests")
    public CodingRunResponse runTests(@PathVariable Long id, @PathVariable Long questionId,
                                      @Valid @RequestBody RunTestsRequest request,
                                      @org.springframework.security.core.annotation.AuthenticationPrincipal
                                      com.itilms.common.security.AppPrincipal student) {
        CodingRunResponse response = attemptService.runTests(id, questionId, request.sourceCode());
        // A run that passed every test is compared with other students' best; anything else comes back unchanged.
        return comparisons.attach(response, student == null ? null : student.profileId());
    }

    /** The code to test. */
    public record RunTestsRequest(@NotBlank @Size(max = 50000) String sourceCode) {
    }

    @Operation(summary = "Report something the browser noticed in a secure test",
            description = "Leaving the window counts; copying and the like are only recorded. The server decides: a counted "
                    + "violation below the test's limit warns, the one that reaches it ends the attempt (TERMINATED, failed). "
                    + "On a test that is not secure the report is accepted and does nothing.")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/violations")
    public ViolationResponse.Outcome reportViolation(@PathVariable Long id, @Valid @RequestBody ViolationRequest request) {
        return attemptService.recordViolation(id, request);
    }

    @Operation(summary = "What the browser reported during an attempt",
            description = "Trainers of the test and staff. Oldest first; 'counted' says whether it added to the limit.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/{id}/violations")
    public List<ViolationResponse.Entry> violations(@PathVariable Long id) {
        return attemptService.violations(id);
    }

    @Operation(summary = "Submit an attempt",
            description = "Scored on the server from the answer key (Doc S14). Accepted up to a minute "
                    + "after the deadline for network delay; later than that, answers saved before "
                    + "the deadline are what count.")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/submit")
    public AttemptResultResponse submit(@PathVariable Long id, @Valid @RequestBody SubmitAttemptRequest request) {
        return attemptService.submit(id, request);
    }

    @Operation(summary = "An attempt's result",
            description = "The student's own, or any attempt at a test the caller teaches.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/result")
    public AttemptResultResponse result(@PathVariable Long id) {
        return attemptService.result(id);
    }
}
