package com.itilms.certificate.dto.response;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the public check of an ID card shows: enough to confirm that the person holding the card is who it says,
 * and nothing about them beyond that. No phone, no address, no batch.
 */
@Schema(description = "Public ID card verification")
public record IdCardVerificationResponse(
        String cardNo,
        @Schema(description = "VALID, EXPIRED or REVOKED")
        String status,
        String fullName,
        String personType,
        String designation,
        LocalDate validUntil,
        String issuedBy
) {
}
