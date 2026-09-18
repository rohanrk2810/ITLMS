package com.itilms.batch.entity;

/** How a student was marked for one session (Doc S6.9). */
public enum AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE,
    EXCUSED;

    /**
     * Whether this counts toward the attendance percentage.
     *
     * <p>LATE counts: the student was taught. EXCUSED does not count against
     * them either - an approved absence should not cost someone a certificate
     * (Doc S7.3) - so it is excluded from the denominator rather than counted
     * as a presence.
     */
    public boolean countsAsPresent() {
        return this == PRESENT || this == LATE;
    }

    /** EXCUSED sessions drop out of the percentage entirely. */
    public boolean countsInDenominator() {
        return this != EXCUSED;
    }
}
