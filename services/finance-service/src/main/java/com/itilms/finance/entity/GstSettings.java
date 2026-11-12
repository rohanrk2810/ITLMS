package com.itilms.finance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The GST rate, the institute's GSTIN and SAC, and the date the rate starts to apply. A single row. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "gst_settings")
public class GstSettings {

    @Id
    private Short id = 1;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percent = BigDecimal.ZERO;

    @Column(length = 15)
    private String gstin;

    @Column(name = "sac_code", length = 10)
    private String sacCode;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "set_by")
    private Long setBy;

    @Column(name = "set_at")
    private Instant setAt;
}
