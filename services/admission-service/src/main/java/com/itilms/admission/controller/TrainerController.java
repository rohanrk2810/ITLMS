package com.itilms.admission.controller;

import java.util.List;

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

import com.itilms.admission.dto.request.CreateTrainerRequest;
import com.itilms.admission.dto.request.UpdateTrainerRequest;
import com.itilms.admission.dto.response.TrainerResponse;
import com.itilms.admission.dto.response.TrainerSummaryResponse;
import com.itilms.admission.service.TrainerService;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.Roles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Trainer profiles and course qualifications (Doc S6.3). */
@Tag(name = "Trainers", description = "Trainer profiles and course allocation")
@RestController
@RequestMapping("/api/trainers")
@RequiredArgsConstructor
public class TrainerController {

    private final TrainerService trainerService;

    @Operation(summary = "List trainers")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping
    public PageResponse<TrainerSummaryResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        return trainerService.search(status, query, pageable);
    }

    @Operation(summary = "My trainer profile")
    @PreAuthorize("hasRole('TRAINER')")
    @GetMapping("/me")
    public TrainerResponse me(@AuthenticationPrincipal AppPrincipal principal) {
        return trainerService.getByUserId(principal.userId());
    }

    @Operation(summary = "Get a trainer")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/{id}")
    public TrainerResponse get(@PathVariable Long id) {
        return trainerService.get(id);
    }

    @Operation(summary = "Create a trainer",
            description = "Creates the login account and the profile together.")
    @PreAuthorize(Roles.STAFF)
    @PostMapping
    public ResponseEntity<TrainerResponse> create(@Valid @RequestBody CreateTrainerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trainerService.create(request));
    }

    @Operation(summary = "Update a trainer",
            description = "Sending courseIds replaces the whole qualification list.")
    @PreAuthorize(Roles.STAFF)
    @PutMapping("/{id}")
    public TrainerResponse update(@PathVariable Long id,
                                  @Valid @RequestBody UpdateTrainerRequest request) {
        return trainerService.update(id, request);
    }

    @Operation(summary = "Trainers available for a course",
            description = "Active trainers cleared to teach this course. Populates the trainer "
                    + "picker when a coordinator creates a batch.")
    @PreAuthorize(Roles.STAFF)
    @GetMapping("/available")
    public List<TrainerSummaryResponse> available(@RequestParam Long courseId) {
        return trainerService.findAvailableForCourse(courseId);
    }

    @Operation(summary = "Resolve many trainer ids", description = "Internal bulk lookup.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/internal/lookup")
    public List<TrainerSummaryResponse> lookup(@RequestBody List<Long> trainerIds) {
        return trainerService.findByIds(trainerIds);
    }
}
