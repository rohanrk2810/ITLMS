package com.itilms.placement.dto.response;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** Doc S15 placement dashboard: open jobs, applications, shortlisted, interviews, selected. */
@Schema(description = "The placement pipeline at a glance")
public record PlacementDashboardResponse(
        long openJobs,
        long totalApplications,
        @Schema(description = "Applications at each stage")
        Map<String, Long> applicationsByStage,
        long selected,
        @Schema(description = "Selections by company, most first")
        List<CompanyPlacements> placementsByCompany
) {

    public record CompanyPlacements(Long companyId, String companyName, long selected) {
    }
}
