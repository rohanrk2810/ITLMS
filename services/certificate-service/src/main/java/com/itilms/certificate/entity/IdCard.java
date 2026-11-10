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

/** An identity card for a student, trainer or staff member. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "id_cards")
public class IdCard extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_no", nullable = false, length = 40)
    private String cardNo;

    @Column(name = "verification_code", nullable = false, length = 16)
    private String verificationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", nullable = false, length = 10)
    private IdCardPersonType personType;

    /** The admission-service student or trainer id; null for staff. */
    @Column(name = "person_id")
    private Long personId;

    @Column(name = "user_id")
    private Long userId;

    /** The student code or the trainer's employee code. */
    @Column(name = "person_code", length = 40)
    private String personCode;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    /** What the card says under the name: "Student", "Trainer", or a staff title. */
    @Column(length = 120)
    private String designation;

    /** The person's own phone number, copied at issue and printed on the card. */
    @Column(length = 30)
    private String phone;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "batch_name", length = 160)
    private String batchName;

    @Column(name = "has_photo", nullable = false)
    private boolean hasPhoto;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private IdCardStatus status = IdCardStatus.ISSUED;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoked_reason", length = 255)
    private String revokedReason;

    @Column(name = "issued_by")
    private Long issuedBy;

    public boolean isValid(LocalDate today) {
        return status == IdCardStatus.ISSUED && !validUntil.isBefore(today);
    }

    public void revoke(String reason, Long byUserId, Instant at) {
        this.status = IdCardStatus.REVOKED;
        this.revokedReason = reason;
        this.revokedBy = byUserId;
        this.revokedAt = at;
    }
}
