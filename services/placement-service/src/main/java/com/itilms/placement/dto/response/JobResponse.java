package com.itilms.placement.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.itilms.placement.entity.JobOpening;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A job opening. The student view adds whether they may apply and why not;
 * the staff view adds how many have applied.
 */
@Schema(description = "A job opening")
public record JobResponse(
        Long id,
        Long companyId,
        String companyName,
        String title,
        String description,
        String jobType,
        String location,
        String packageOffered,
        Integer openings,
        LocalDate applicationDeadline,
        Set<Long> eligibleCourseIds,
        boolean requireCertificate,
        Integer minAttendancePercent,
        Integer minScorePercent,
        String eligibilityNotes,
        String status,
        Instant publishedAt,

        @Schema(description = "Staff view: applications received")
        Long applicationCount,

        @Schema(description = "Student view: whether they may apply")
        Boolean eligible,
        @Schema(description = "Student view: what stands in the way")
        List<String> ineligibleReasons,
        @Schema(description = "Student view: their application's stage, if they have applied")
        String myApplicationStage
) {

    public static JobResponse forStaff(JobOpening j, String companyName, long applications) {
        return build(j, companyName, applications, null, null, null);
    }

    public static JobResponse forStudent(JobOpening j, String companyName, boolean eligible,
                                         List<String> reasons, String myStage) {
        return build(j, companyName, null, eligible, reasons, myStage);
    }

    private static JobResponse build(JobOpening j, String companyName, Long applications,
                                     Boolean eligible, List<String> reasons, String myStage) {
        return new JobResponse(j.getId(), j.getCompanyId(), companyName, j.getTitle(), j.getDescription(),
                j.getJobType().name(), j.getLocation(), j.getPackageOffered(), j.getOpenings(),
                j.getApplicationDeadline(), Set.copyOf(j.getEligibleCourseIds()), j.isRequireCertificate(),
                j.getMinAttendancePercent(), j.getMinScorePercent(), j.getEligibilityNotes(),
                j.getStatus().name(), j.getPublishedAt(), applications, eligible, reasons, myStage);
    }
}
