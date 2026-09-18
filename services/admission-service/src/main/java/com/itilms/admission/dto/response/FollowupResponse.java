package com.itilms.admission.dto.response;

import java.time.Instant;

import com.itilms.admission.entity.LeadFollowup;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One recorded follow-up attempt")
public record FollowupResponse(
        Long id,
        Long leadId,
        Instant contactedAt,
        String outcome,
        String remark,
        Instant nextActionAt,
        Long createdBy
) {

    public static FollowupResponse from(LeadFollowup followup) {
        return new FollowupResponse(
                followup.getId(),
                followup.getLeadId(),
                followup.getContactedAt(),
                followup.getOutcome().name(),
                followup.getRemark(),
                followup.getNextActionAt(),
                followup.getCreatedBy());
    }
}
