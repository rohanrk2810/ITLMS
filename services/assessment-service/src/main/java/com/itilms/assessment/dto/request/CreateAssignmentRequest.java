package com.itilms.assessment.dto.request;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "A piece of work set for a batch")
public record CreateAssignmentRequest(

        @NotNull(message = "Batch is required")
        Long batchId,

        Long courseId,

        @NotBlank(message = "Title is required")
        @Size(max = 200)
        String title,

        String instructions,

        @Schema(description = "Handle of the brief in file-service")
        @Size(max = 120)
        String attachmentRef,

        @NotNull(message = "A deadline is required")
        @Future(message = "The deadline must be in the future")
        Instant dueAt,

        @NotNull(message = "Maximum marks are required")
        @Min(value = 1, message = "Maximum marks must be at least 1")
        @Max(value = 1000, message = "Maximum marks cannot exceed 1000")
        Integer maxMarks,

        @Schema(description = "Accept work after the deadline, marked LATE. Default true.")
        Boolean allowLate,

        @Schema(description = "Counts toward course completion. Default true.")
        Boolean mandatory,

        @Schema(description = "Save without showing it to students yet. Default false: the batch "
                + "sees it, and is notified, as soon as it is created.")
        Boolean draft
) {
}
