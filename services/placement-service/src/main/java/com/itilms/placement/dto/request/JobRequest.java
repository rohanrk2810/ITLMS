package com.itilms.placement.dto.request;

import java.time.LocalDate;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "A job opening and its eligibility rules (Doc S6.14, S7.4)")
public record JobRequest(
        @NotNull(message = "Company is required") Long companyId,
        @NotBlank(message = "Title is required") @Size(max = 200) String title,
        String description,
        @Schema(description = "FULL_TIME, INTERNSHIP, CONTRACT or PART_TIME") String jobType,
        @Size(max = 160) String location,
        @Schema(example = "4.5 LPA") @Size(max = 100) String packageOffered,
        @Min(value = 1, message = "Openings must be at least 1") Integer openings,
        LocalDate applicationDeadline,

        @Schema(description = "Courses whose students may apply. Empty means any course.")
        Set<Long> eligibleCourseIds,
        @Schema(description = "Only students holding a certificate in one of those courses")
        Boolean requireCertificate,
        @Min(0) @Max(100) Integer minAttendancePercent,
        @Min(0) @Max(100) Integer minScorePercent,
        String eligibilityNotes
) {
}
