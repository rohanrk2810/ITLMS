package com.itilms.liveclass.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** How often a student actually joined the live classes held for their batches, for their progress report. */
@Schema(description = "A student's live-class participation")
public record StudentParticipationResponse(
        @Schema(description = "Live classes that have finished, in the student's batches")
        int sessionsHeld,
        @Schema(description = "Of those, how many the student joined")
        int sessionsJoined,
        int minutesInRoom,
        @Schema(description = "Mean of the attendance percentage the room computed for each class joined; null when none")
        Integer averageAttendancePercent,
        Instant lastJoinedAt
) {
}
