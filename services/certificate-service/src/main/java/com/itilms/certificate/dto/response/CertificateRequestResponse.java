package com.itilms.certificate.dto.response;

import java.time.Instant;

import com.itilms.certificate.entity.CertificateRequest;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A student's request for a certificate")
public record CertificateRequestResponse(
        Long id,
        Long studentId,
        String studentCode,
        String studentName,
        Long courseId,
        String courseTitle,
        Long batchId,
        String batchName,
        @Schema(description = "PENDING, APPROVED, REJECTED or ISSUED")
        String status,
        Instant requestedAt,
        Instant reviewedAt,
        @Schema(description = "Why it was rejected; only set when REJECTED")
        String rejectionReason,
        @Schema(description = "The certificate, once ISSUED")
        Long certificateId
) {

    public static CertificateRequestResponse from(CertificateRequest r) {
        return new CertificateRequestResponse(r.getId(), r.getStudentId(), r.getStudentCode(), r.getStudentName(),
                r.getCourseId(), r.getCourseTitle(), r.getBatchId(), r.getBatchName(), r.getStatus().name(),
                r.getRequestedAt(), r.getReviewedAt(), r.getRejectionReason(), r.getCertificateId());
    }
}
