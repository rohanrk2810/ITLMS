package com.itilms.batch.entity;

/** Where a batch is in its life cycle. */
public enum BatchStatus {
    PLANNED,
    ONGOING,
    COMPLETED,
    CANCELLED;

    /** New students may only join a batch that has not finished. */
    public boolean acceptsEnrollment() {
        return this == PLANNED || this == ONGOING;
    }

    public boolean isRunning() {
        return this == ONGOING;
    }
}
