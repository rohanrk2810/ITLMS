package com.itilms.assessment.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

import com.itilms.assessment.dto.request.CreateQuizRequest;
import com.itilms.assessment.dto.request.QuestionRequest;
import com.itilms.assessment.dto.response.AttemptResultResponse;
import com.itilms.assessment.dto.response.AttemptViewResponse;
import com.itilms.assessment.dto.response.QuizResponse;
import com.itilms.assessment.dto.response.StudentQuizResponse;
import com.itilms.assessment.service.AttemptService;
import com.itilms.assessment.service.QuizService;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.Roles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** MCQ tests (Doc S6.11, S11). */
@Tag(name = "Tests", description = "Writing tests and starting attempts")
@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;
    private final AttemptService attemptService;

    // ------------------------------------------------------------ authoring

    @Operation(summary = "Create a test",
            description = "Created as a draft. Leave batchId blank to open it to every batch of the course.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping
    public ResponseEntity<QuizResponse> create(@Valid @RequestBody CreateQuizRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quizService.create(request));
    }

    @Operation(summary = "Change a draft test's settings",
            description = "Refused once published: changing a test students have already sat would "
                    + "make their results incomparable.")
    @PreAuthorize(Roles.ACADEMIC)
    @PutMapping("/{id}")
    public QuizResponse update(@PathVariable Long id, @Valid @RequestBody CreateQuizRequest request) {
        return quizService.update(id, request);
    }

    @Operation(summary = "A test with its answer key", description = "Trainers of the batch or course, and staff.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/{id}")
    public QuizResponse get(@PathVariable Long id) {
        return quizService.get(id);
    }

    @Operation(summary = "List tests", description = "By course. Staff may omit the course to see every test.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping
    public PageResponse<QuizResponse> list(@RequestParam(required = false) Long courseId,
                                           @PageableDefault(size = 20) Pageable pageable) {
        return quizService.list(courseId, pageable);
    }

    @Operation(summary = "Add a question",
            description = "A single-choice or true/false question needs exactly one correct option.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/{id}/questions")
    public QuizResponse addQuestion(@PathVariable Long id, @Valid @RequestBody QuestionRequest request) {
        return quizService.addQuestion(id, request);
    }

    @Operation(summary = "Edit a question")
    @PreAuthorize(Roles.ACADEMIC)
    @PutMapping("/questions/{questionId}")
    public QuizResponse updateQuestion(@PathVariable Long questionId, @Valid @RequestBody QuestionRequest request) {
        return quizService.updateQuestion(questionId, request);
    }

    @Operation(summary = "Remove a question")
    @PreAuthorize(Roles.ACADEMIC)
    @DeleteMapping("/questions/{questionId}")
    public QuizResponse deleteQuestion(@PathVariable Long questionId) {
        return quizService.deleteQuestion(questionId);
    }

    @Operation(summary = "Publish a test", description = "Needs at least one question. The batch is notified.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/{id}/publish")
    public QuizResponse publish(@PathVariable Long id) {
        return quizService.publish(id);
    }

    @Operation(summary = "Close a test",
            description = "No new attempts. Running attempts are scored as they stand, and results "
                    + "held back are released to students.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/{id}/close")
    public QuizResponse close(@PathVariable Long id) {
        return quizService.close(id);
    }

    @Operation(summary = "Every result for a test", description = "The trainer's results sheet, best score first.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/{id}/results")
    public List<AttemptResultResponse> results(@PathVariable Long id) {
        return attemptService.quizResults(id);
    }

    // ------------------------------------------------------------ students

    @Operation(summary = "Tests I can take", description = "With my attempts and best result so far.")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/available")
    public List<StudentQuizResponse> available() {
        return quizService.availableToMe();
    }

    @Operation(summary = "Start (or resume) an attempt",
            description = "Returns the paper without the answer key. If an attempt is already running "
                    + "it is resumed, with the same deadline and saved answers.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The paper"),
            @ApiResponse(responseCode = "403", description = "The test is not for your batch"),
            @ApiResponse(responseCode = "422", description = "Not open, or no attempts left")
    })
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/attempts")
    public AttemptViewResponse start(@PathVariable Long id) {
        return attemptService.start(id);
    }

    @Operation(summary = "My attempts at a test")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/{id}/attempts/mine")
    public List<AttemptResultResponse> myAttempts(@PathVariable Long id) {
        return attemptService.myAttempts(id);
    }
}
