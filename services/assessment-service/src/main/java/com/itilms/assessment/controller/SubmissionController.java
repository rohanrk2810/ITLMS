package com.itilms.assessment.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.assessment.dto.request.EvaluateSubmissionRequest;
import com.itilms.assessment.dto.response.SubmissionResponse;
import com.itilms.assessment.service.AssignmentService;
import com.itilms.common.security.Roles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Marking (Doc S6.10, S11). */
@Tag(name = "Submissions", description = "Viewing and marking handed-in work")
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final AssignmentService assignmentService;

    @Operation(summary = "One submission",
            description = "A student sees only their own; a trainer only their batches'.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public SubmissionResponse get(@PathVariable Long id) {
        return assignmentService.getSubmission(id);
    }

    @Operation(summary = "Mark a submission, or return it for rework",
            description = "The student is notified and the change is audited.")
    @PreAuthorize(Roles.ACADEMIC)
    @PutMapping("/{id}/evaluate")
    public SubmissionResponse evaluate(@PathVariable Long id,
                                       @Valid @RequestBody EvaluateSubmissionRequest request) {
        return assignmentService.evaluate(id, request);
    }
}
