package com.itilms.liveclass.service;

import java.util.List;

import com.itilms.common.event.SessionScheduledEvent;
import com.itilms.liveclass.dto.response.JoinTokenResponse;
import com.itilms.liveclass.dto.response.LiveParticipantResponse;
import com.itilms.liveclass.dto.response.LiveSessionResponse;
import com.itilms.liveclass.entity.LiveSession;

/** Running an online class: rooms, entry, and closing up afterwards. */
public interface LiveClassService {

    /**
     * Makes sure a live room record exists for a scheduled session, reading the
     * timetable on the caller's behalf.
     *
     * <p>Idempotent. Used on first Join for sessions whose scheduling event
     * never reached this service - created before it was deployed, say.
     */
    LiveSession provision(Long classSessionId);

    /**
     * Creates or updates the room record from a timetable event.
     *
     * <p>Separate from {@link #provision} because event consumers run with no
     * signed-in user, so they cannot call batch-service; the event carries
     * everything needed. A session switched to classroom-only has its room
     * cancelled.
     */
    void applySchedule(SessionScheduledEvent event);

    /**
     * Issues a join token for the signed-in caller.
     *
     * <p>Every authorization decision for a live class happens here: whether the
     * caller belongs in this class at all, and what they may do once inside.
     */
    JoinTokenResponse join(Long classSessionId);

    /** Full view including who attended. Trainers of the batch and staff only. */
    LiveSessionResponse get(Long liveSessionId);

    /** Summary without the participant list - safe for any signed-in user. */
    LiveSessionResponse byClassSession(Long classSessionId);

    /** Live classes coming up for the signed-in user. */
    List<LiveSessionResponse> upcomingForCaller();

    List<LiveSessionResponse> forBatch(Long batchId);

    /** The signed-in student's own room time in one batch. */
    List<LiveParticipantResponse> myRoomTime(Long batchId);

    /**
     * Ends the class deliberately, rather than waiting for the room to empty.
     *
     * <p>Settles everyone still inside, triggers the attendance calculation and
     * closes the room. Trainers of the batch and staff only.
     */
    LiveSessionResponse endClass(Long liveSessionId);

    /** Removes one participant from a running class. Trainers of the batch and staff only. */
    void removeParticipant(Long liveSessionId, Long userId);

    /** Marks the room for a called-off class as cancelled and tears it down. */
    void cancelForClassSession(Long classSessionId, String reason);
}
