package com.itilms.batch.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.batch.dto.request.CreateBatchRequest;
import com.itilms.batch.dto.request.EnrollStudentRequest;
import com.itilms.batch.dto.request.UpdateBatchRequest;
import com.itilms.batch.dto.response.BatchResponse;
import com.itilms.batch.dto.response.BatchSummaryResponse;
import com.itilms.batch.dto.response.EnrollmentResponse;
import com.itilms.batch.dto.response.EnrollmentResultResponse;
import com.itilms.batch.service.BatchService;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.Roles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Batches and enrolment (Doc S6.6, S11). */
@Tag(name = "Batches", description = "Batches and enrolment")
@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    @Operation(summary = "List batches")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public PageResponse<BatchSummaryResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long trainerId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return batchService.search(status, courseId, trainerId, mode, query, pageable);
    }

    @Operation(summary = "My batches",
            description = "For a student, the batches they are enrolled in. For a trainer, "
                    + "the batches they teach — including ones where they are a co-trainer.")
    @PreAuthorize("hasAnyRole('STUDENT','TRAINER')")
    @GetMapping("/mine")
    public List<BatchSummaryResponse> mine(@AuthenticationPrincipal AppPrincipal principal) {
        if (principal.profileId() == null) {
            throw new ForbiddenOperationException(
                    "Your account is not linked to a student or trainer profile yet.");
        }
        return principal.isStudent()
                ? batchService.myBatches(principal.profileId())
                : batchService.trainerBatches(principal.profileId());
    }

    @Operation(summary = "Get a batch")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public BatchResponse get(@PathVariable Long id) {
        return batchService.get(id);
    }

    @Operation(summary = "Create a batch",
            description = "The batch code is generated from the course code and the year.")
    @PreAuthorize(Roles.STAFF)
    @PostMapping
    public ResponseEntity<BatchResponse> create(@Valid @RequestBody CreateBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(batchService.create(request));
    }

    @Operation(summary = "Update a batch",
            description = "Capacity cannot be reduced below the number already enrolled.")
    @PreAuthorize(Roles.STAFF)
    @PutMapping("/{id}")
    public BatchResponse update(@PathVariable Long id, @Valid @RequestBody UpdateBatchRequest request) {
        return batchService.update(id, request);
    }

    @Operation(summary = "Enrol students",
            description = "Checks capacity and rejects a second active enrolment for the same "
                    + "student (Doc S14). Reports the outcome per student rather than failing "
                    + "the whole request on one problem.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Processed; check outcomes for per-student results"),
            @ApiResponse(responseCode = "422", description = "The batch is closed to enrolment")
    })
    @PreAuthorize(Roles.STAFF)
    @PostMapping("/{id}/students")
    public EnrollmentResultResponse enroll(@PathVariable Long id,
                                           @Valid @RequestBody EnrollStudentRequest request) {
        return batchService.enroll(id, request);
    }

    @Operation(summary = "Batch roster", description = "Active students in the batch.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/{id}/students")
    public List<EnrollmentResponse> roster(@PathVariable Long id) {
        return batchService.roster(id);
    }

    @Operation(summary = "Drop a student from a batch")
    @PreAuthorize(Roles.STAFF)
    @PostMapping("/enrollments/{enrollmentId}/drop")
    public EnrollmentResponse drop(@PathVariable Long enrollmentId,
                                   @RequestParam(required = false) String reason) {
        return batchService.dropStudent(enrollmentId, reason);
    }

    @Operation(summary = "Transfer a student to another batch",
            description = "Only between batches of the same course.")
    @PreAuthorize(Roles.STAFF)
    @PostMapping("/enrollments/{enrollmentId}/transfer")
    public EnrollmentResponse transfer(@PathVariable Long enrollmentId,
                                       @RequestParam Long targetBatchId) {
        return batchService.transferStudent(enrollmentId, targetBatchId);
    }

    @Operation(summary = "Batch counts by status")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/stats/counts")
    public Map<String, Long> counts() {
        return batchService.counts();
    }

    @Operation(summary = "Resolve many batch ids", description = "Internal bulk lookup.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/internal/lookup")
    public List<BatchSummaryResponse> lookup(@RequestBody List<Long> batchIds) {
        return batchService.findByIds(batchIds);
    }

    @Operation(summary = "Is this student in this batch?",
            description = "Internal check used by liveclass-service before it lets somebody into "
                    + "a live class. Answers only yes or no, so the caller learns nothing about "
                    + "the rest of the register.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/internal/{batchId}/enrolled/{studentId}")
    public EnrollmentCheck isEnrolled(@PathVariable Long batchId, @PathVariable Long studentId) {
        return new EnrollmentCheck(batchService.isActivelyEnrolled(batchId, studentId));
    }

    /** The answer to "may this student be here?", and nothing else. */
    public record EnrollmentCheck(boolean enrolled) {
    }
}
