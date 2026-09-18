package com.itilms.finance.entity;

/** Where a fee plan stands. */
public enum FeePlanStatus {
    /** Money is still owed, or could be. */
    ACTIVE,
    /** Paid in full. Reopens to ACTIVE if a payment is later reversed. */
    SETTLED,
    /** Withdrawn before any money was taken - a student who never joined. */
    CANCELLED
}
