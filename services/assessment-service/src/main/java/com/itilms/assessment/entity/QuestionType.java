package com.itilms.assessment.entity;

/**
 * What shape of answer a question takes.
 *
 * <p>The source document models four fixed option columns and a single
 * correct one (S10), which cannot express any of these beyond the first.
 * S5 asks for an "MCQ/coding-ready framework"; this is the MCQ half, with
 * room for the other to be added as another type.
 */
public enum QuestionType {
    /** Exactly one option is right; picking two is an invalid answer. */
    SINGLE_CHOICE,
    /** Several options are right, and all of them are needed to score. */
    MULTI_CHOICE,
    /** Two options, one right. Stored like any other choice question. */
    TRUE_FALSE;

    public boolean allowsMultipleSelections() {
        return this == MULTI_CHOICE;
    }
}
