package com.itilms.finance.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One head of a person's pay: an earning or a deduction, fixed or a percent of the basic. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "staff_pay_heads")
public class StaffPayHead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** EARNING or DEDUCTION. */
    @Column(nullable = false, length = 10)
    private String kind;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "percent_of_basic", precision = 5, scale = 2)
    private BigDecimal percentOfBasic;

    @Column(name = "is_basic", nullable = false)
    private boolean basic;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
