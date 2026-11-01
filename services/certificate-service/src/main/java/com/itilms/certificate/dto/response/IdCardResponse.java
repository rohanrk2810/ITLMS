package com.itilms.certificate.dto.response;

import java.time.LocalDate;

import com.itilms.certificate.entity.IdCard;

public record IdCardResponse(
        Long id,
        String cardNo,
        String verificationCode,
        String verificationUrl,
        String personType,
        Long personId,
        Long userId,
        String personCode,
        String fullName,
        String designation,
        Long batchId,
        String batchName,
        boolean hasPhoto,
        LocalDate issuedOn,
        LocalDate validUntil,
        /** ISSUED, REVOKED, or EXPIRED once the valid-until date has passed. */
        String status,
        String revokedReason
) {
    public static IdCardResponse from(IdCard c, String verificationUrl, LocalDate today) {
        String status = c.getStatus().name();
        if (c.getStatus() == com.itilms.certificate.entity.IdCardStatus.ISSUED && c.getValidUntil().isBefore(today)) {
            status = "EXPIRED";
        }
        return new IdCardResponse(c.getId(), c.getCardNo(), c.getVerificationCode(), verificationUrl,
                c.getPersonType().name(), c.getPersonId(), c.getUserId(), c.getPersonCode(), c.getFullName(),
                c.getDesignation(), c.getBatchId(), c.getBatchName(), c.isHasPhoto(), c.getIssuedOn(),
                c.getValidUntil(), status, c.getRevokedReason());
    }
}
