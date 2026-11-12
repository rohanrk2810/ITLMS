package com.itilms.finance.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.itilms.common.entity.AuditableEntity;

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

/** One day's cash count: what the system recorded, what was in the drawer, and the difference. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cash_closings")
public class CashClosing extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "closing_date", nullable = false)
    private LocalDate closingDate;

    @Column(name = "expected_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "counted_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal countedCash;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal difference;

    @Column(length = 255)
    private String note;
}
