package com.itilms.batch.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

/**
 * Add students to a batch (Doc S11, {@code POST /api/batches/{id}/students}).
 *
 * <p>Takes a list because coordinators enrol a whole intake at once. The service
 * reports per-student outcomes rather than failing the entire call on one
 * problem — enrolling nineteen of twenty and being told which one needs
 * attention beats being told "one of these twenty is already enrolled".
 */
@Schema(description = "Enrol one or more students into a batch")
public record EnrollStudentRequest(

        @NotEmpty(message = "At least one student is required")
        List<Long> studentIds,
        @Schema(description = "Leave blank to place them in the whole batch (the batch fee, every course). Give one "
                + "of the batch's courses to place them in that course alone, at its course fee.")
        Long courseId
) {

    /** The whole batch. */
    public EnrollStudentRequest(List<Long> studentIds) {
        this(studentIds, null);
    }
}
