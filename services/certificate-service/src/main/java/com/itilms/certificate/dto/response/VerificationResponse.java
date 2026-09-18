package com.itilms.certificate.dto.response;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the public verification page shows (Doc S6.13).
 *
 * <p>Deliberately little: enough for an employer to confirm that this person
 * completed this course on this date, and nothing else about them - no student
 * code, no batch, no contact details.
 */
@Schema(description = "Public certificate verification")
public record VerificationResponse(
        String certificateNo,
        @Schema(description = "VALID, or REVOKED for a certificate that has been withdrawn")
        String status,
        String studentName,
        String courseTitle,
        LocalDate issueDate,
        String issuedBy
) {
}
