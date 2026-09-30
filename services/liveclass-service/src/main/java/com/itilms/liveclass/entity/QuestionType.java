package com.itilms.liveclass.entity;

/** The kinds of question a trainer can ask in a live class. */
public enum QuestionType {
    MCQ, MULTIPLE_SELECT, TRUE_FALSE, SHORT_ANSWER, CODING, OTHER;

    /** Questions answered by picking from a list. */
    public boolean isChoice() {
        return this == MCQ || this == MULTIPLE_SELECT || this == TRUE_FALSE;
    }
}
