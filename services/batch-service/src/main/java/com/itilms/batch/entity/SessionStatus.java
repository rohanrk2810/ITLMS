package com.itilms.batch.entity;

/** The state of one scheduled lecture. */
public enum SessionStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED,
    RESCHEDULED;

    /** Attendance only makes sense for a class that actually happened. */
    public boolean allowsAttendance() {
        return this == SCHEDULED || this == COMPLETED;
    }
}
