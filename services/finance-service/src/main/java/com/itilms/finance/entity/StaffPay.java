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

    @Column(name = "employee_code", length = 40)
    private String employeeCode;

    @Column(length = 10)
    private String pan;

    @Column(name = "bank_name", length = 80)
    private String bankName;

    @Column(name = "bank_account", length = 30)
    private String bankAccount;

    @Column(name = "pf_number", length = 40)
    private String pfNumber;

    @Column(length = 20)
    private String uan;

    @Column(name = "esi_number", length = 30)
    private String esiNumber;

    @Column(name = "hired_on")
    private java.time.LocalDate hiredOn;

    @Column(length = 80)
    private String department;

    @Column(length = 120)
    private String location;
}
