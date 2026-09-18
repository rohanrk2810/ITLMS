package com.itilms.batch.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Update a batch.
 *
 * <p>{@code courseId} is absent. Moving a running batch to a different course
 * would invalidate every enrolment, every progress record and every certificate
 * criterion attached to it. If a batch was created against the wrong course, the
 * honest fix is to cancel it and create the right one.
 */
@Schema(description = "Update a batch")
public record UpdateBatchRequest(

        @Size(max = 120)
        String name,

        Long trainerId,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        LocalDate endDate,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        LocalTime endTime,

        List<String> classDays,

        @Schema(allowableValues = {"ONLINE", "OFFLINE", "HYBRID"})
        String mode,

        @Min(1) @Max(500)
        Integer capacity,

        @Size(max = 60)
        String classroom,

        @Size(max = 600)
        String meetingUrl,

        @Schema(allowableValues = {"PLANNED", "ONGOING", "COMPLETED", "CANCELLED"})
        @NotBlank(message = "Status is required")
        String status,

        List<Long> coTrainerIds
) {
}
