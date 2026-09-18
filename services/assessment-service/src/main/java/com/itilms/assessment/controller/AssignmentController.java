package com.itilms.assessment.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.assessment.dto.request.CreateAssignmentRequest;
import com.itilms.assessment.dto.request.SubmitAssignmentRequest;
import com.itilms.assessment.dto.response.AssignmentResponse;
import com.itilms.assessment.dto.response.SubmissionResponse;
import com.itilms.assessment.service.AssignmentService;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.Roles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Assignments (Doc S6.10, S11). */
@Tag(name = "Assignments", description = "Setting work and handing it in")
@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @Operation(summary = "Set an assignment",
            description = "Published to the batch straight away unless draft=true. A trainer may "
                    + "only set work for a batch they teach.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "403", description = "Not a batch you teach")
    })
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping
    public ResponseEntity<AssignmentResponse> create(@Valid @RequestBody CreateAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.create(request));
    }

    @Operation(summary = "Edit an assignment",
            description = "The maximum marks cannot drop below a mark already awarded.")
    @PreAuthorize(Roles.ACADEMIC)
    @PutMapping("/{id}")
    public AssignmentResponse update(@PathVariable Long id, @Valid @RequestBody CreateAssignmentRequest request) {
        return assignmentService.update(id, request);
    }

    @Operation(summary = "Publish a draft", description = "The batch is notified.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/{id}/publish")
    public AssignmentResponse publish(@PathVariable Long id) {
        return assignmentService.publish(id);
    }

    @Operation(summary = "Stop accepting submissions", description = "Marking can continue.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/{id}/close")
    public AssignmentResponse close(@PathVariable Long id) {
        return assignmentService.close(id);
    }

    @Operation(summary = "One assignment",
            description = "Students see it with their own submission; trainers with the batch's counts.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public AssignmentResponse get(@PathVariable Long id) {
        return assignmentService.get(id);
    }

    @Operation(summary = "A batch's assignments", description = "With submitted and marked counts.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/batches/{batchId}")
    public PageResponse<AssignmentResponse> forBatch(@PathVariable Long batchId,
                                                     @PageableDefault(size = 20) Pageable pageable) {
        return assignmentService.forBatch(batchId, pageable);
    }

    @Operation(summary = "My assignments", description = "Published work in my batches, with my submissions.")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/mine")
    public List<AssignmentResponse> mine() {
        return assignmentService.mine();
    }

    @Operation(summary = "Hand work in",
            description = "Text, files already uploaded to file-service, or both. Submitting again "
                    + "before it is marked replaces the earlier version. Work after the deadline "
                    + "is marked LATE (Doc S14), or refused if the assignment does not allow it.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Received"),
            @ApiResponse(responseCode = "403", description = "Not enrolled in the batch"),
            @ApiResponse(responseCode = "422", description = "Empty, closed, too late, or already marked")
    })
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/submissions")
    public ResponseEntity<SubmissionResponse> submit(@PathVariable Long id,
                                                     @Valid @RequestBody SubmitAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.submit(id, request));
    }

    @Operation(summary = "Every submission for an assignment")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/{id}/submissions")
    public List<SubmissionResponse> submissions(@PathVariable Long id) {
        return assignmentService.submissions(id);
    }

    @Operation(summary = "Marking queue", description = "Unmarked work in a batch, oldest first.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/batches/{batchId}/awaiting-evaluation")
    public List<SubmissionResponse> awaitingEvaluation(@PathVariable Long batchId) {
        return assignmentService.awaitingEvaluation(batchId);
    }
}
