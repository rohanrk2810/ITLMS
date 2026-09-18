package com.itilms.certificate.entity;

import java.time.Instant;
import java.time.LocalDate;

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

/** A course certificate (Doc S6.13). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "certificates")
public class Certificate extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "certificate_no", nullable = false, length = 40)
    private String certificateNo;

    @Column(name = "verification_code", nullable = false, length = 16)
    private String verificationCode;

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

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CertificateStatus status = CertificateStatus.ISSUED;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoked_reason", length = 255)
    private String revokedReason;

    /** The figures each criterion was checked against at issue, as JSON. */
    @Column(columnDefinition = "text")
    private String evidence;

    @Column(name = "issued_by")
    private Long issuedBy;

    public boolean isValid() {
        return status == CertificateStatus.ISSUED;
    }

    public void revoke(String reason, Long byUserId, Instant at) {
        this.status = CertificateStatus.REVOKED;
        this.revokedReason = reason;
        this.revokedBy = byUserId;
        this.revokedAt = at;
    }
}
