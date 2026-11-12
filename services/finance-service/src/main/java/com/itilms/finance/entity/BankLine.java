package com.itilms.finance.entity;

import java.math.BigDecimal;
import java.time.Instant;
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

/** One line of the bank statement: money in (credit) or out (debit), and what it was matched to. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bank_statement_lines")
public class BankLine extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_date", nullable = false)
    private LocalDate lineDate;

    @Column(nullable = false, length = 255)
    @Builder.Default
    private String description = "";

    @Column(length = 80)
    private String reference;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(name = "import_key", nullable = false, length = 300)
    private String importKey;

    /** PAYMENT, EXPENSE or REFUND; empty while the line is unmatched. */
    @Column(name = "matched_kind", length = 20)
    private String matchedKind;

    @Column(name = "matched_id")
    private Long matchedId;

    @Column(name = "matched_at")
    private Instant matchedAt;

    @Column(name = "matched_by")
    private Long matchedBy;

    public boolean isMatched() {
        return matchedKind != null;
    }

    public boolean isCredit() {
        return credit.signum() > 0;
    }

    public BigDecimal amount() {
        return isCredit() ? credit : debit;
    }

    /** What to look for a reference in: the reference column and the description together. */
    public String searchText() {
        return ((reference == null ? "" : reference) + " " + description).toLowerCase(java.util.Locale.ROOT);
    }
}
