package com.itilms.assessment.entity;

/**
 * What shape of answer a question takes.
 *
 * <p>The source document models four fixed option columns and a single
 * correct one (S10), which cannot express any of these beyond the first.
 * S5 asks for an "MCQ/coding-ready framework": the choice types are the MCQ
 * half, SHORT_ANSWER and CODING the rest.
 */
public enum QuestionType {
    /** Exactly one option is right; picking two is an invalid answer. */
    SINGLE_CHOICE,
    /** Several options are right, and all of them are needed to score. */
    MULTI_CHOICE,
    /** Two options, one right. Stored like any other choice question. */
    TRUE_FALSE,
    /** A typed answer, marked against the trainer's list of accepted answers. */
    SHORT_ANSWER,
    /** A program, marked by running it against the trainer's test cases. */
    CODING;

    public boolean allowsMultipleSelections() {
        return this == MULTI_CHOICE;
    }

    /** Answered by ticking options, and marked from the option key. */
    public boolean isChoice() {
        return this == SINGLE_CHOICE || this == MULTI_CHOICE || this == TRUE_FALSE;
    }

    /** Answered with text (a sentence, or source code) instead of options. */
    public boolean isText() {
        return this == SHORT_ANSWER || this == CODING;
    }
}
