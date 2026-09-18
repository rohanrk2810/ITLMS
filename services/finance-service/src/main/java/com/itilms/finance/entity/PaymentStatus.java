package com.itilms.finance.entity;

/** Whether a payment counts. */
public enum PaymentStatus {
    /** Counts toward what the student has paid (Doc S14). */
    SUCCESS,
    /**
     * Undone - a bounced cheque, a charge-back, an entry made in error.
     * Kept, not deleted: the receipt was issued and that has to stay on record.
     */
    REVERSED
}
