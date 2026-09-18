package com.itilms.assessment.entity;

/** What has happened to a student's submission. */
public enum SubmissionStatus {
    SUBMITTED,
    /**
     * Handed in after the deadline (Doc S14). A status rather than a refusal:
     * the trainer decides what a late submission is worth, and a student who
     * did the work deserves it on record.
     */
    LATE,
    /** Marked and given feedback. */
    EVALUATED,
    /** Sent back for rework; the student may submit again. */
    RETURNED;

    public boolean isEvaluated() {
        return this == EVALUATED;
    }

    /** Whether the student may still replace their work. */
    public boolean allowsResubmission() {
        return this == SUBMITTED || this == LATE || this == RETURNED;
    }
}
