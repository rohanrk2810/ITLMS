package com.itilms.batch.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Extending how long a course runs, for a whole batch or for one student. */
public final class ValidityDtos {

    private ValidityDtos() {
    }

    @Schema(description = "Extend a batch: a later end date, and why. A finished batch runs again.")
    public record ExtendBatchRequest(
            @NotNull(message = "Choose the new end date") LocalDate endDate,
            @NotBlank(message = "Say why the course is being extended") @Size(max = 255) String reason) {
    }

    @Schema(description = "Set or extend how long one student may use the course. An expired enrolment opens again.")
    public record ExtendAccessRequest(
            @NotNull(message = "Choose the date until which the student may use the course") LocalDate accessUntil,
            @NotBlank(message = "Say why the access is being extended") @Size(max = 255) String reason) {
    }

    @Schema(description = "What an extension changed")
    public record ExtendBatchResult(Long batchId, String batchCode, LocalDate previousEndDate, LocalDate endDate,
                                    String status, int studentsExtended) {
    }
}
