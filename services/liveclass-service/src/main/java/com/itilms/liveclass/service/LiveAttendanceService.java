package com.itilms.liveclass.service;

import java.time.Instant;

/** Turns time in a room into an attendance verdict. */
public interface LiveAttendanceService {

    /**
     * Closes out a finished class and publishes its attendance.
     *
     * <p>Runs once per session. Everyone still shown as being in the room has
     * their last stint closed, each student's total becomes a percentage and a
     * PRESENT/LATE/ABSENT verdict, and the result goes to batch-service, which
     * owns the register.
     *
     * <p>Doing nothing on a session that has already been settled is deliberate
     * rather than defensive: a late webhook or a re-run of the sweep must not
     * publish a second verdict and overwrite a correction the trainer has since
     * made by hand.
     *
     * @param closedAt when the class ended - the moment a trainer pressed End - or
     *                 {@code null} to work it out from the room's own activity
     * @return true if this call settled the session, false if it was already done
     */
    boolean settle(Long liveSessionId, Instant closedAt);
}
