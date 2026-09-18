package com.itilms.placement.dto.response;

import java.time.Instant;

import com.itilms.placement.entity.JobApplication;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An application and where it stands")
public record ApplicationResponse(
        Long id, Long jobId, String jobTitle, String companyName,
        Long studentId, String studentName, Long courseId,
        String resumeRef, String coverNote, Instant appliedAt,
        @Schema(description = "APPLIED, SHORTLISTED, INTERVIEW, ON_HOLD, SELECTED, REJECTED or WITHDRAWN")
        String stage,
        Integer currentRound, Instant nextInterviewAt, String offerDetails, Instant decidedAt
) {

    public static ApplicationResponse of(JobApplication a, String jobTitle, String companyName) {
        return new ApplicationResponse(a.getId(), a.getJobId(), jobTitle, companyName, a.getStudentId(),
                a.getStudentName(), a.getCourseId(), a.getResumeRef(), a.getCoverNote(), a.getAppliedAt(),
                a.getStage().name(), a.getCurrentRound(), a.getNextInterviewAt(), a.getOfferDetails(),
                a.getDecidedAt());
    }
}
