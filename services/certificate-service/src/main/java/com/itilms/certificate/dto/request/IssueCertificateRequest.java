package com.itilms.certificate.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Who, and for which course.
 *
 * <p>No batch id: the batch is read from the student's own enrolment. Letting
 * the caller name it would let someone pick a batch with no sessions on record
 * and pass the attendance condition by default.
 */
@Schema(description = "Issue a certificate")
public record IssueCertificateRequest(
        @Schema(description = "Ignored when a student claims their own certificate")
        Long studentId,

        @NotNull(message = "Course is required")
        Long courseId
) {
}
