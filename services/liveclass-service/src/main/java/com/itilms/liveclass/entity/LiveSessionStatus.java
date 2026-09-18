package com.itilms.liveclass.entity;

/** Where a live room is in its short life. */
public enum LiveSessionStatus {

    /** The room exists on LiveKit (or will be created on first join) but nobody has entered. */
    SCHEDULED,

    /** At least one person is in the room. */
    LIVE,

    /** The room closed. Attendance is computed from this point, not before. */
    ENDED,

    /** The class was called off; no attendance is published for it. */
    CANCELLED;

    public boolean isSettled() {
        return this == ENDED || this == CANCELLED;
    }
}
