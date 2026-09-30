package com.itilms.certificate.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A request with the completion criteria re-checked now, so a reviewer decides on today's facts,
 * not the ones at request time (a payment may have been reversed since).
 */
@Schema(description = "A certificate request and the student's eligibility right now")
public record CertificateRequestDetailResponse(
        CertificateRequestResponse request,
        @Schema(description = "Absent for a trainer, who may see the request but not run the checks")
        EligibilityResponse eligibility
) {
}
