package com.itilms.batch.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Putting a course into a batch, and changing who teaches it there. */
public final class BatchCourseRequests {

    private BatchCourseRequests() {
    }

    @Schema(description = "Add a course to a batch; every student placed in the whole batch is enrolled in it")
    public record Add(
            @NotNull(message = "Course is required")
            Long courseId,
            @Schema(description = "Who teaches it in this batch. May be assigned later.")
            Long trainerId
    ) {
    }

    @Schema(description = "Change the trainer of a course in a batch")
    public record ChangeTrainer(
            @Schema(description = "Blank takes the trainer off")
            Long trainerId
    ) {
    }
}
