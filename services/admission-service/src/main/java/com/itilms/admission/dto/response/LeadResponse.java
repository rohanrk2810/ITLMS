package com.itilms.admission.dto.response;

import java.time.Instant;

import com.itilms.admission.entity.Lead;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lead / enquiry")
public record LeadResponse(
        Long id,
        String name,
        String phone,
        String email,
        String source,
        String status,
        Long interestedCourseId,
        Long counselorUserId,
        Instant nextFollowUpAt,
        @Schema(description = "True when the follow-up date has passed and nobody has acted")
        boolean overdue,
        String notes,
        String lostReason,
        Long convertedStudentId,
        Instant convertedAt,
        long followupCount,
        Instant createdAt
) {

    public static LeadResponse from(Lead lead, long followupCount) {
        return new LeadResponse(
                lead.getId(),
                lead.getName(),
                lead.getPhone(),
                lead.getEmail(),
                lead.getSource().name(),
                lead.getStatus().name(),
                lead.getInterestedCourseId(),
                lead.getCounselorUserId(),
                lead.getNextFollowUpAt(),
                lead.isOverdue(),
                lead.getNotes(),
                lead.getLostReason(),
                lead.getConvertedStudentId(),
                lead.getConvertedAt(),
                followupCount,
                lead.getCreatedAt());
    }

    public static LeadResponse from(Lead lead) {
        return from(lead, 0);
    }
}
