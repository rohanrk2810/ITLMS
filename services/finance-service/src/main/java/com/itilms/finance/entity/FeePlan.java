package com.itilms.finance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.itilms.common.entity.AuditableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What one student owes for one course, and when (Doc S6.12). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fee_plans")
public class FeePlan extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "student_user_id")
    private Long studentUserId;

    @Column(name = "student_code", length = 40)
    private String studentCode;

    @Column(name = "student_name", length = 160)
    private String studentName;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "total_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalFee;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "net_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal netFee;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FeePlanStatus status = FeePlanStatus.ACTIVE;

    @Column(length = 500)
    private String notes;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_reason", length = 255)
    private String cancelledReason;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "fee_plan_id")
    @OrderBy("installmentNo ASC")
    @Builder.Default
    private List<FeeInstallment> installments = new ArrayList<>();

    /**
     * Sets the fee and derives the net amount from it.
     *
     * <p>The only way net fee is written, so the column and the database's
     * {@code net_fee = total_fee - discount} check can never disagree.
     */
    public void setTerms(BigDecimal totalFee, BigDecimal discount) {
        this.totalFee = money(totalFee);
        this.discount = money(discount == null ? BigDecimal.ZERO : discount);
        this.netFee = this.totalFee.subtract(this.discount);
    }

    /** Replaces the schedule. Callers check that it adds up to the net fee first. */
    public void replaceInstallments(List<FeeInstallment> schedule) {
        this.installments.clear();
        this.installments.addAll(schedule);
    }

    public void cancel(String reason, Instant at) {
        this.status = FeePlanStatus.CANCELLED;
        this.cancelledReason = reason;
        this.cancelledAt = at;
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
