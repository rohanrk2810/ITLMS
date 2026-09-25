package com.itilms.batch.entity;

/** Where a course access request stands. Only PENDING can change; the rest are final. */
public enum CourseRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Withdrawn by the student before anyone decided. */
    CANCELLED;

    public boolean isOpen() {
        return this == PENDING;
    }
}
