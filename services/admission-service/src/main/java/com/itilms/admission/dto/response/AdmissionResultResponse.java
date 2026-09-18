package com.itilms.admission.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a completed admission produced.
 *
 * <p>Conversion touches four services. Reporting back which records were created
 * lets the client navigate straight to the new student, and gives support staff
 * something concrete to check when part of the chain has not caught up yet.
 *
 * <p>{@code feePlanRequested} is a request, not a confirmation: finance-service
 * creates the plan from an event, so at the moment this response is written the
 * plan is imminent rather than present.
 */
@Schema(description = "Result of converting a lead into an admitted student")
public record AdmissionResultResponse(
        Long leadId,
        Long studentId,
        String studentCode,
        Long userId,
        String email,
        Long courseId,
        Long batchId,
        @Schema(description = "A fee plan has been requested from finance-service")
        boolean feePlanRequested,
        String message
) {
}
