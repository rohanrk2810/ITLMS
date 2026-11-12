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

/** One person's salary slip for one month. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payslips")
public class Payslip extends AuditableEntity {

    public enum Status { PENDING, APPROVED, PAID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slip_no", nullable = false, length = 30)
    private String slipNo;

    /** "2026-11". */
    @Column(name = "month_key", nullable = false, length = 7)
    private String monthKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "staff_name", nullable = false, length = 160)
    private String staffName;

    @Column(nullable = false, length = 60)
    private String designation;

    @Column(name = "days_in_month", nullable = false)
    private int daysInMonth;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal basic;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal allowances;

    @Column(name = "fixed_deduction", nullable = false, precision = 12, scale = 2)
    private BigDecimal fixedDeduction;

    @Column(name = "lwp_days", nullable = false, precision = 4, scale = 1)
    @Builder.Default
    private BigDecimal lwpDays = BigDecimal.ZERO;

    @Column(name = "lwp_deduction", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal lwpDeduction = BigDecimal.ZERO;

    @Column(name = "other_deduction", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal otherDeduction = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal bonus = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal gross;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal net;

    @Column(length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "pay_method", length = 20)
    private String payMethod;

    @Column(name = "pay_reference", length = 80)
    private String payReference;

    @Column(name = "expense_id")
    private Long expenseId;

    /** The total deducted: the fixed deduction, leave without pay, and anything else. */
    public BigDecimal totalDeductions() {
        return fixedDeduction.add(lwpDeduction).add(otherDeduction);
    }
}
