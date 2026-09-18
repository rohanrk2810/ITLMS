package com.itilms.placement.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where an application is in the pipeline (Doc S7.4: Applies -> Shortlisted
 * -> Interview Rounds -> Selected / Rejected / On Hold).
 *
 * <p>The allowed moves are written down here rather than left open. Moving a
 * rejected candidate straight to SELECTED, or reopening an application the
 * student withdrew, is almost always a slip of the mouse on a dropdown - and
 * a selection is what the institute reports as a placement.
 */
public enum ApplicationStage {

    APPLIED,
    SHORTLISTED,
    /** One or more interview rounds; the round number travels with it. */
    INTERVIEW,
    ON_HOLD,
    SELECTED,
    REJECTED,
    /** The student pulled out. Only the student does this. */
    WITHDRAWN;

    public boolean isFinal() {
        return this == SELECTED || this == REJECTED || this == WITHDRAWN;
    }

    public Set<ApplicationStage> allowedNext() {
        return switch (this) {
            case APPLIED -> EnumSet.of(SHORTLISTED, REJECTED, ON_HOLD, WITHDRAWN);
            case SHORTLISTED -> EnumSet.of(INTERVIEW, REJECTED, ON_HOLD, WITHDRAWN);
            // INTERVIEW -> INTERVIEW is the next round.
            case INTERVIEW -> EnumSet.of(INTERVIEW, SELECTED, REJECTED, ON_HOLD, WITHDRAWN);
            case ON_HOLD -> EnumSet.of(SHORTLISTED, INTERVIEW, SELECTED, REJECTED, WITHDRAWN);
            case SELECTED, REJECTED, WITHDRAWN -> EnumSet.noneOf(ApplicationStage.class);
        };
    }

    public boolean canMoveTo(ApplicationStage next) {
        return allowedNext().contains(next);
    }
}
