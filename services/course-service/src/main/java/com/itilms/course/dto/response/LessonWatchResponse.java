package com.itilms.course.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** How one enrolled student got on with one video lesson. */
@Schema(description = "One student's viewing of a video lesson")
public record LessonWatchResponse(
        Long studentId,
        Long batchId,
        @Schema(description = "Share of the video actually watched (not skipped), 0-100")
        int watchedPercent,
        int playedSeconds,
        int durationSeconds,
        int faceAwaySeconds,
        int facePauses,
        boolean completed
) {
}
