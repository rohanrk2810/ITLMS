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

/** GST paid to the government (a challan), against what the institute owes. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gst_payments")
public class GstPayment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    /** The month of the return it is for, such as "2026-03". */
    @Column(name = "for_month", length = 7)
    private String forMonth;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String method;

    @Column(length = 80)
    private String reference;

    @Column(length = 255)
    private String note;
}
