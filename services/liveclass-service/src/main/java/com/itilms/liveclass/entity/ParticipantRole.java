package com.itilms.liveclass.entity;

/**
 * What someone is allowed to do in a live room.
 *
 * <p>This drives the grants in the join token, so it is a security decision and
 * not a label: a STUDENT token cannot be edited in the browser into a TRAINER
 * one, because the grants are signed server-side.
 */
public enum ParticipantRole {

    /** Runs the class: publishes media and may remove a disruptive participant. */
    TRAINER,

    /** Attends the class. Publishes media, but has no room administration rights. */
    STUDENT,

    /**
     * An admin or coordinator sitting in to observe.
     *
     * <p>Counted in the room but never in the register - a coordinator dropping
     * in for five minutes is not a student with 8% attendance.
     */
    STAFF;

    public boolean countsTowardsAttendance() {
        return this == STUDENT;
    }

    public boolean isRoomAdmin() {
        return this == TRAINER;
    }
}
