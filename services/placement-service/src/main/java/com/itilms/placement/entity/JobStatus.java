package com.itilms.placement.entity;

/** Where a job opening stands. */
public enum JobStatus {
    /** Being written; not visible to students. */
    DRAFT,
    /** Visible to eligible students and accepting applications. */
    OPEN,
    /** No longer accepting applications; the pipeline carries on. */
    CLOSED,
    /** Withdrawn by the company. */
    CANCELLED
}
