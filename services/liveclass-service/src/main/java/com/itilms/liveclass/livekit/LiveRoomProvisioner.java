package com.itilms.liveclass.livekit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.liveclass.config.LiveClassProperties;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.repository.LiveSessionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Makes sure the LiveKit room for a class exists, with timeouts that fit the class.
 *
 * <p>LiveKit closes rooms by itself, on two clocks: one for a room nobody has
 * entered yet, one for a room everybody has left. Both are set per room here
 * because the server-wide defaults are wrong for a timetable:
 * <ul>
 *   <li>A room created half an hour before class must not close for want of
 *       participants before the class has even started. It is kept open until
 *       the join window shuts.</li>
 *   <li>A room everyone has dropped out of - the trainer's router restarting -
 *       must survive long enough for them to come back.</li>
 * </ul>
 *
 * <p>Creation is requested on every join, not only when this service believes
 * no room exists. The stored sid can be stale: LiveKit may have closed the room
 * since, and handing out a token for a room that no longer exists produces a
 * failed connection that looks, to a student, like the class is broken.
 * createRoom is idempotent and cheap, so asking again is the reliable choice.
 */
@Component
@RequiredArgsConstructor
public class LiveRoomProvisioner {

    /** Never ask LiveKit to wait less than this for a first participant. */
    private static final int MIN_EMPTY_TIMEOUT_SECONDS = 300;

    private final LiveKitGateway liveKit;
    private final LiveSessionRepository sessionRepository;
    private final LiveClassProperties props;
    private final ObjectMapper objectMapper;

    public void ensureRoom(LiveSession session) {
        String sid = liveKit.createRoom(
                session.getRoomName(),
                metadata(session),
                emptyTimeoutSeconds(session, Instant.now()),
                props.getDepartureTimeoutSeconds(),
                session.getMaxParticipants());

        if (sid != null && !sid.equals(session.getLivekitRoomSid())) {
            sessionRepository.recordRoomSid(session.getId(), sid);
            session.setLivekitRoomSid(sid);
        }
    }

    /** Until the join window closes: the scheduled end plus the late-join grace. */
    int emptyTimeoutSeconds(LiveSession session, Instant now) {
        Instant windowCloses = session.getScheduledEndAt()
                .plus(Duration.ofMinutes(props.getLateJoinGraceMinutes()));
        long seconds = Duration.between(now, windowCloses).toSeconds();
        return (int) Math.max(MIN_EMPTY_TIMEOUT_SECONDS, seconds);
    }

    private String metadata(LiveSession session) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "classSessionId", session.getClassSessionId(),
                    "batchId", session.getBatchId(),
                    "batchCode", String.valueOf(session.getBatchCode())));
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
