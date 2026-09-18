package com.itilms.placement.controller;

import java.util.List;

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

import com.itilms.common.security.Roles;
import com.itilms.placement.dto.request.CompanyRequest;
import com.itilms.placement.dto.request.StageChangeRequest;
import com.itilms.placement.dto.response.ApplicationResponse;
import com.itilms.placement.dto.response.CompanyResponse;
import com.itilms.placement.dto.response.PlacementDashboardResponse;
import com.itilms.placement.dto.response.StageChangeResponse;
import com.itilms.placement.service.ApplicationService;
import com.itilms.placement.service.CompanyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Companies, applications and placement records. */
final class PlacementControllers {

    private static final String PLACEMENT_VIEW = "hasAnyRole('ADMIN','PLACEMENT','COORDINATOR')";

    private PlacementControllers() {
    }

    @Tag(name = "Companies", description = "Recruiters")
    @RestController
    @RequestMapping("/api/companies")
    @RequiredArgsConstructor
    static class CompanyController {

        private final CompanyService companyService;

        @Operation(summary = "List companies")
        @PreAuthorize(PLACEMENT_VIEW)
        @GetMapping
        public List<CompanyResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
            return companyService.list(activeOnly);
        }

        @Operation(summary = "One company")
        @PreAuthorize(PLACEMENT_VIEW)
        @GetMapping("/{id}")
        public CompanyResponse get(@PathVariable Long id) {
            return companyService.get(id);
        }

        @Operation(summary = "Add a company")
        @PreAuthorize(Roles.PLACEMENT_DESK)
        @PostMapping
        public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CompanyRequest request) {
            return ResponseEntity.status(HttpStatus.CREATED).body(companyService.create(request));
        }

        @Operation(summary = "Update a company")
        @PreAuthorize(Roles.PLACEMENT_DESK)
        @PutMapping("/{id}")
        public CompanyResponse update(@PathVariable Long id, @Valid @RequestBody CompanyRequest request) {
            return companyService.update(id, request);
        }
    }

    @Tag(name = "Applications", description = "The interview pipeline")
    @RestController
    @RequestMapping("/api/applications")
    @RequiredArgsConstructor
    static class ApplicationController {

        private final ApplicationService applicationService;

        @Operation(summary = "My applications", description = "A student sees only their own (Doc S6.14).")
        @PreAuthorize("hasRole('STUDENT')")
        @GetMapping("/me")
        public List<ApplicationResponse> mine() {
            return applicationService.mine();
        }

        @Operation(summary = "Move an application through the pipeline",
                description = "Only allowed moves are accepted; each is recorded and audited (Doc S14).")
        @PreAuthorize(Roles.PLACEMENT_DESK)
        @PutMapping("/{id}/stage")
        public ApplicationResponse changeStage(@PathVariable Long id, @Valid @RequestBody StageChangeRequest request) {
            return applicationService.changeStage(id, request);
        }

        @Operation(summary = "Withdraw my application", description = "Until the company has decided.")
        @PreAuthorize("hasRole('STUDENT')")
        @PostMapping("/{id}/withdraw")
        public ApplicationResponse withdraw(@PathVariable Long id) {
            return applicationService.withdraw(id);
        }

        @Operation(summary = "An application's history")
        @PreAuthorize("hasAnyRole('ADMIN','PLACEMENT','COORDINATOR','STUDENT')")
        @GetMapping("/{id}/history")
        public List<StageChangeResponse> history(@PathVariable Long id) {
            return applicationService.history(id);
        }
    }

    @Tag(name = "Placements", description = "Placement records and the dashboard")
    @RestController
    @RequestMapping("/api/placements")
    @RequiredArgsConstructor
    static class PlacementController {

        private final ApplicationService applicationService;

        @Operation(summary = "Placement records", description = "Every selection, most recent first (Doc S7.4).")
        @PreAuthorize(PLACEMENT_VIEW)
        @GetMapping
        public List<ApplicationResponse> placements() {
            return applicationService.placements();
        }

        @Operation(summary = "Placement dashboard", description = "Doc S15.")
        @PreAuthorize(PLACEMENT_VIEW)
        @GetMapping("/dashboard")
        public PlacementDashboardResponse dashboard() {
            return applicationService.dashboard();
        }
    }
}
