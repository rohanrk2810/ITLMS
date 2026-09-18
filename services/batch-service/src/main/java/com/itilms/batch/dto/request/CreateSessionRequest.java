package com.itilms.batch.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Schedule one lecture (Doc S6.7). */
@Schema(description = "Create a class session")
public record CreateSessionRequest(

        @NotNull(message = "Batch is required")
        Long batchId,

        @NotNull(message = "Session date is required")
        LocalDate sessionDate,

        @Schema(description = "Defaults to the batch's usual start time")
        LocalTime startTime,

        @Schema(description = "Defaults to the batch's usual end time")
        LocalTime endTime,

        @Size(max = 255)
        String topic,

        @Schema(description = "Overrides the batch mode for this session only",
                allowableValues = {"ONLINE", "OFFLINE", "HYBRID"})
        String mode,

        @Schema(description = "Leave blank for online sessions; liveclass-service supplies the room")
        @Size(max = 600)
        String meetingUrl,

        @Size(max = 60)
        String room,

        @Schema(description = "Overrides the batch's primary trainer for this session")
        Long trainerId
) {
}
