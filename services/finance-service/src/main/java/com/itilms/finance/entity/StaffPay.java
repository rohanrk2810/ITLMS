package com.itilms.finance.entity;

import java.math.BigDecimal;

import com.itilms.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What one member of staff is paid each month: basic, allowances and a fixed deduction. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "staff_pay")
public class StaffPay extends AuditableEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "staff_name", nullable = false, length = 160)
    private String staffName;

    @Column(name = "role_name", nullable = false, length = 40)
    private String roleName;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal basic = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal allowances = BigDecimal.ZERO;

    @Column(name = "fixed_deduction", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal fixedDeduction = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
