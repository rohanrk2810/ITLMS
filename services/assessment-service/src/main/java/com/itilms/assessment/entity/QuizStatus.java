package com.itilms.assessment.entity;

/** Where a test is in its life (Doc S6.11). */
public enum QuizStatus {
    DRAFT,
    PUBLISHED,
    CLOSED;

    public boolean isOpenToStudents() {
        return this == PUBLISHED;
    }
}
