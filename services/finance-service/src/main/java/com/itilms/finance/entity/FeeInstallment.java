package com.itilms.finance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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

/**
 * One slice of the fee, due on one date.
 *
 * <p>Deliberately has no paid amount or status. How much of it is paid is
 * worked out from the plan's payments every time it is asked for (see
 * {@code FeeLedger}), so a reversed payment is reflected at once and the two
 * can never disagree.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fee_installments")
public class FeeInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "installment_no", nullable = false)
    private int installmentNo;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** The name a late fee is given, so it can be told from other extra charges and waived. */
    public static final String LATE_FEE_LABEL = "Late fee";

    public boolean isLateFee() {
        return LATE_FEE_LABEL.equals(label);
    }

    /** The name of an extra charge ("Development fee"); empty for a part of the course fee. */
    @Column(length = 120)
    private String label;

    /** The last "due in N days" reminder sent, so each goes out once. */
    @Column(name = "last_reminder_days")
    private Integer lastReminderDays;

    /** When a late fee was added for this installment; null until then. */
    @Column(name = "late_fee_charged_at")
    private Instant lateFeeChargedAt;

    @Column(name = "overdue_notified_at")
    private Instant overdueNotifiedAt;
}
