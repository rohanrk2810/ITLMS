package com.itilms.placement.controller;

import java.util.List;

import org.springframework.data.domain.PageImpl;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.Roles;
import com.itilms.common.security.SecurityUtils;
import com.itilms.placement.dto.request.ApplyRequest;
import com.itilms.placement.dto.request.JobRequest;
import com.itilms.placement.dto.response.ApplicationResponse;
import com.itilms.placement.dto.response.JobResponse;
import com.itilms.placement.service.ApplicationService;
import com.itilms.placement.service.JobService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Job openings (Doc S6.14, S11). */
@Tag(name = "Jobs", description = "Job openings and applying for them")
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private static final String PLACEMENT_VIEW = "hasAnyRole('ADMIN','PLACEMENT','COORDINATOR')";

    private final JobService jobService;
    private final ApplicationService applicationService;

    @Operation(summary = "List job openings",
            description = "Doc S11. A student gets every open job, each saying whether they may apply "
                    + "and, if not, why. Placement staff get every job, filterable by status or company.")
    @PreAuthorize("hasAnyRole('ADMIN','PLACEMENT','COORDINATOR','STUDENT')")
    @GetMapping
    public PageResponse<JobResponse> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) Long companyId,
                                          @PageableDefault(size = 20) Pageable pageable) {
        if (SecurityUtils.requirePrincipal().isStudent()) {
            List<JobResponse> open = jobService.openJobsForStudent();
            return PageResponse.from(new PageImpl<>(open));
        }
        return jobService.list(status, companyId, pageable);
    }

    @Operation(summary = "One job opening")
    @PreAuthorize("hasAnyRole('ADMIN','PLACEMENT','COORDINATOR','STUDENT')")
    @GetMapping("/{id}")
    public JobResponse get(@PathVariable Long id) {
        return jobService.get(id);
    }

    @Operation(summary = "Create a job opening", description = "Created as a draft.")
    @PreAuthorize(Roles.PLACEMENT_DESK)
    @PostMapping
    public ResponseEntity<JobResponse> create(@Valid @RequestBody JobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.create(request));
    }

    @Operation(summary = "Edit a job opening",
            description = "Once students have applied, eligibility rules may be relaxed but not tightened.")
    @PreAuthorize(Roles.PLACEMENT_DESK)
    @PutMapping("/{id}")
    public JobResponse update(@PathVariable Long id, @Valid @RequestBody JobRequest request) {
        return jobService.update(id, request);
    }

    @Operation(summary = "Publish", description = "Opens applications and notifies eligible students.")
    @PreAuthorize(Roles.PLACEMENT_DESK)
    @PostMapping("/{id}/publish")
    public JobResponse publish(@PathVariable Long id) {
        return jobService.publish(id);
    }

    @Operation(summary = "Close applications", description = "The pipeline carries on for those who applied.")
    @PreAuthorize(Roles.PLACEMENT_DESK)
    @PostMapping("/{id}/close")
    public JobResponse close(@PathVariable Long id) {
        return jobService.close(id, false);
    }

    @Operation(summary = "Cancel the opening", description = "The company withdrew it.")
    @PreAuthorize(Roles.PLACEMENT_DESK)
    @PostMapping("/{id}/cancel")
    public JobResponse cancel(@PathVariable Long id) {
        return jobService.close(id, true);
    }

    @Operation(summary = "Apply", description = "Doc S11. Eligibility is checked when applying, not just shown.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Applied"),
            @ApiResponse(responseCode = "409", description = "Already applied"),
            @ApiResponse(responseCode = "422", description = "Not eligible, or applications have closed")
    })
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/apply")
    public ResponseEntity<ApplicationResponse> apply(@PathVariable Long id, @Valid @RequestBody ApplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.apply(id, request));
    }

    @Operation(summary = "Applications for a job", description = "The recruiter's pipeline view.")
    @PreAuthorize(PLACEMENT_VIEW)
    @GetMapping("/{id}/applications")
    public List<ApplicationResponse> applications(@PathVariable Long id) {
        return applicationService.forJob(id);
    }
}
