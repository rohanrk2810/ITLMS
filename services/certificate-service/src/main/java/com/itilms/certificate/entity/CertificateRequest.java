package com.itilms.certificate.entity;

import java.time.Instant;

import com.itilms.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A student's request for a course certificate, awaiting an admin's decision. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "certificate_requests")
public class CertificateRequest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "student_user_id")
    private Long studentUserId;

    @Column(name = "student_code", length = 40)
    private String studentCode;

    @Column(name = "student_name", nullable = false, length = 160)
    private String studentName;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "course_title", nullable = false, length = 200)
    private String courseTitle;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "batch_name", length = 160)
    private String batchName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CertificateRequestStatus status = CertificateRequestStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private Instant requestedAt = Instant.now();

    @Column(name = "eligibility_snapshot", columnDefinition = "text")
    private String eligibilitySnapshot;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "certificate_id")
    private Long certificateId;

    public void approve(Long adminId, Instant at) {
        this.status = CertificateRequestStatus.APPROVED;
        this.reviewedBy = adminId;
        this.reviewedAt = at;
    }

    public void reject(Long adminId, String reason, Instant at) {
        this.status = CertificateRequestStatus.REJECTED;
        this.reviewedBy = adminId;
        this.reviewedAt = at;
        this.rejectionReason = reason;
    }

    public void markIssued(Long certificateId) {
        this.status = CertificateRequestStatus.ISSUED;
        this.certificateId = certificateId;
    }
}
