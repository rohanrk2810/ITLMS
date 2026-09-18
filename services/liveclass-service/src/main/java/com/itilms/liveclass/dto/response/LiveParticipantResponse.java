package com.itilms.liveclass.dto.response;

import java.time.Instant;

import com.itilms.liveclass.entity.LiveParticipant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One person's time in a live class")
public record LiveParticipantResponse(
        Long id,
        Long userId,
        Long studentId,
        String displayName,
        String role,
        Instant firstJoinedAt,
        Instant lastLeftAt,

        @Schema(description = "True while this person is in the room right now")
        boolean inRoom,

        @Schema(description = "Total time in the room, summed over every reconnect")
        int attendedSeconds,

        @Schema(description = "How many times they entered - a high count usually means a bad connection")
        int joinCount,

        @Schema(description = "Share of the class they were present for; null until the class ends")
        Integer attendancePercent,

        @Schema(description = "PRESENT, LATE or ABSENT; null until the class ends")
        String computedStatus
) {

    public static LiveParticipantResponse from(LiveParticipant p) {
        return new LiveParticipantResponse(
                p.getId(), p.getUserId(), p.getStudentId(), p.getDisplayName(),
                p.getRole().name(), p.getFirstJoinedAt(), p.getLastLeftAt(), p.inRoom(),
                p.getAttendedSeconds(), p.getJoinCount(),
                p.getAttendancePercent(), p.getComputedStatus());
    }
}
