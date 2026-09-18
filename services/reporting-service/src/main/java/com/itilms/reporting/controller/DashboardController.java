package com.itilms.reporting.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.reporting.dto.DashboardSummaryResponse;
import com.itilms.reporting.service.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Institute-wide counters (Doc S15). Eventually consistent - see docs/02, "Dashboards and reports". */
@Tag(name = "Dashboard", description = "Institute-wide counters, built from events")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @Operation(summary = "Institute summary", description = "Cached for itilms.reporting.dashboard-cache-seconds.")
    @PreAuthorize(Roles.STAFF)
    @GetMapping("/summary")
    public DashboardSummaryResponse summary() {
        return service.summary();
    }
}
