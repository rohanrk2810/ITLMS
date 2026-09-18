package com.itilms.finance.entity;

import java.math.BigDecimal;
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

/** Money received against a fee plan (Doc S6.12). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payments")
public class Payment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fee_plan_id", nullable = false)
    private Long feePlanId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "reference_no", length = 80)
    private String referenceNo;

    @Column(name = "receipt_no", nullable = false, length = 40)
    private String receiptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.SUCCESS;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;

    @Column(length = 500)
    private String notes;

    public boolean counts() {
        return status == PaymentStatus.SUCCESS;
    }

    public void reverse(String reason, Long byUserId, Instant at) {
        this.status = PaymentStatus.REVERSED;
        this.reversalReason = reason;
        this.reversedBy = byUserId;
        this.reversedAt = at;
    }
}
