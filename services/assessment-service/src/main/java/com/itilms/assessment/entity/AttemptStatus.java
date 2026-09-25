package com.itilms.assessment.entity;

/** Where one sitting of a test is. */
public enum AttemptStatus {
    /** Started, still within its deadline. */
    IN_PROGRESS,
    /** Submitted and scored. */
    SUBMITTED,
    /**
     * The deadline passed without a submission.
     *
     * <p>Scored on whatever was answered rather than discarded: a student
     * whose connection died ten minutes before the end keeps their work.
     */
    EXPIRED,
    /**
     * Ended by the secure-test rules: too many violations. Scored on what was answered, for the
     * record, and always marked failed.
     */
    TERMINATED;

    public boolean isFinished() {
        return this != IN_PROGRESS;
    }
}
