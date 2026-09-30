package com.itilms.certificate.dto.response;

import java.time.Instant;
import java.time.LocalDate;

import com.itilms.certificate.entity.Certificate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A certificate")
public record CertificateResponse(
        Long id,
        String certificateNo,
        @Schema(description = "Printed beside the number; needed to verify the certificate publicly")
        String verificationCode,
        String verificationUrl,
        Long studentId,
        String studentCode,
        String studentName,
        Long courseId,
        String courseTitle,
        Long batchId,
        String batchName,
        LocalDate issueDate,
        String status,
        Instant revokedAt,
        String revokedReason
) {

    public static CertificateResponse from(Certificate c, String verificationUrl) {
        return new CertificateResponse(c.getId(), c.getCertificateNo(), c.getVerificationCode(), verificationUrl,
                c.getStudentId(), c.getStudentCode(), c.getStudentName(), c.getCourseId(), c.getCourseTitle(),
                c.getBatchId(), c.getBatchName(), c.getIssueDate(), c.getStatus().name(), c.getRevokedAt(), c.getRevokedReason());
    }
}
