package com.itilms.assessment.entity;

/** Where an assignment is in its life (Doc S6.10). */
public enum AssignmentStatus {
    /** Being written. Invisible to students. */
    DRAFT,
    /** Visible to the batch, accepting submissions. */
    PUBLISHED,
    /** No longer accepting submissions; evaluation can continue. */
    CLOSED;

    public boolean acceptsSubmissions() {
        return this == PUBLISHED;
    }
}
