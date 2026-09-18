package com.itilms.finance.entity;

/** How the money arrived (Doc S6.12 "mode"). */
public enum PaymentMethod {
    CASH,
    UPI,
    CARD,
    BANK_TRANSFER,
    CHEQUE,
    ONLINE;

    /**
     * Whether a bank or gateway reference identifies this payment.
     *
     * <p>Required for everything except cash: a UPI payment with no
     * transaction id cannot be matched to a bank statement, and is exactly the
     * entry that gets recorded twice.
     */
    public boolean needsReference() {
        return this != CASH;
    }
}
