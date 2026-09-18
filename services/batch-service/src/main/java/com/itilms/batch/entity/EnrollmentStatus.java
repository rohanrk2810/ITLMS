package com.itilms.batch.entity;

/**
 * A student's standing within a batch.
 *
 * <p>TRANSFERRED is distinct from DROPPED: the student is still studying, just
 * in a different batch. Conflating them would make the drop-out rate look worse
 * than it is and would hide genuine attrition behind timetable reshuffles.
 */
public enum EnrollmentStatus {
    ACTIVE,
    COMPLETED,
    DROPPED,
    SUSPENDED,
    TRANSFERRED;

    public boolean isActive() {
        return this == ACTIVE;
    }

    /** Whether this student should appear on the attendance register. */
    public boolean countsForAttendance() {
        return this == ACTIVE;
    }
}
