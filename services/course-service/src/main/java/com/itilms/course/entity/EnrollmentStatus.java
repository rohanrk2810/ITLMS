package com.itilms.course.entity;

/** Mirrors the enrolment state owned by batch-service. */
public enum EnrollmentStatus {
    ACTIVE,
    COMPLETED,
    DROPPED,
    SUSPENDED;

    /** Only an active enrolment may record new progress. */
    public boolean allowsProgress() {
        return this == ACTIVE;
    }

    /** Whether the student may still open the course content. A finished student keeps it for revision. */
    public boolean allowsContentAccess() {
        return this == ACTIVE || this == COMPLETED;
    }
}
