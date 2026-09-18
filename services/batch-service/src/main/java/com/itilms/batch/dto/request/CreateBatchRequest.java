package com.itilms.batch.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create a batch (Doc S6.6).
 *
 * <p>{@code batchCode} is not a field: it is generated from the course code,
 * the year and a per-course counter. Letting a coordinator type one produces
 * duplicates, typos, and codes that no longer say which course they belong to.
 */
@Schema(description = "Create a batch")
public record CreateBatchRequest(

        @Schema(description = "Optional friendly name, e.g. \"Morning batch\"")
        @Size(max = 120)
        String name,

        @NotNull(message = "Course is required")
        Long courseId,

        @Schema(description = "Primary trainer. May be assigned later.")
        Long trainerId,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @Schema(description = "Expected finish. Used to close the batch automatically.")
        LocalDate endDate,

        @Schema(example = "10:00:00")
        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @Schema(example = "12:00:00")
        @NotNull(message = "End time is required")
        LocalTime endTime,

        @Schema(example = "MON,WED,FRI", description = "Weekdays the batch meets")
        List<String> classDays,

        @Schema(allowableValues = {"ONLINE", "OFFLINE", "HYBRID"})
        String mode,

        @Min(value = 1, message = "Capacity must be at least 1")
        @Max(value = 500, message = "Capacity of more than 500 is not a batch")
        Integer capacity,

        @Size(max = 60)
        String classroom,

        @Schema(description = "Left blank for online batches: liveclass-service provides the room")
        @Size(max = 600)
        String meetingUrl,

        @Schema(description = "Additional trainers sharing this batch")
        List<Long> coTrainerIds
) {
}
