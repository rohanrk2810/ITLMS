package com.itilms.admission.entity;

/**
 * Where a lead sits in the counseling pipeline (Doc S6.4).
 *
 * <p>NOT_INTERESTED and LOST are both dead ends but they are not the same
 * thing: the first is the prospect's decision, the second is ours (unreachable,
 * gone elsewhere). Separating them is what makes a counselor's conversion rate
 * a fair number.
 */
public enum LeadStatus {

    NEW,
    CONTACTED,
    FOLLOW_UP,
    INTERESTED,
    NOT_INTERESTED,
    CONVERTED,
    LOST;

    /** Still worth a counselor's time. */
    public boolean isOpen() {
        return this == NEW || this == CONTACTED || this == FOLLOW_UP || this == INTERESTED;
    }

    public boolean isClosed() {
        return !isOpen();
    }
}
