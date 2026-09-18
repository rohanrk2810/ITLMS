package com.itilms.liveclass.livekit;

import java.time.Instant;

import livekit.LivekitWebhook;

/**
 * The fields of a LiveKit webhook this service acts on, lifted out of protobuf.
 *
 * <p>The handler takes this instead of the generated message so the attendance
 * logic can be tested with plain values, and so the rest of the service does not
 * depend on the shape of LiveKit's wire format.
 *
 * @param id         LiveKit's delivery id; the key that makes a retried delivery a no-op
 * @param occurredAt when LiveKit says it happened, not when it reached us - a
 *                   delivery retried a minute later must not add a minute
 */
public record WebhookNotice(
        String id,
        String event,
        String roomName,
        String roomSid,
        String identity,
        String participantSid,
        Instant occurredAt
) {

    public static final String ROOM_STARTED = "room_started";
    public static final String ROOM_FINISHED = "room_finished";
    public static final String PARTICIPANT_JOINED = "participant_joined";
    public static final String PARTICIPANT_LEFT = "participant_left";

    public static WebhookNotice from(LivekitWebhook.WebhookEvent event) {
        String roomName = event.hasRoom() ? blankToNull(event.getRoom().getName()) : null;
        String roomSid = event.hasRoom() ? blankToNull(event.getRoom().getSid()) : null;
        String identity = event.hasParticipant() ? blankToNull(event.getParticipant().getIdentity()) : null;
        String participantSid = event.hasParticipant() ? blankToNull(event.getParticipant().getSid()) : null;
        Instant at = event.getCreatedAt() > 0 ? Instant.ofEpochSecond(event.getCreatedAt()) : Instant.now();

        return new WebhookNotice(blankToNull(event.getId()), event.getEvent(),
                roomName, roomSid, identity, participantSid, at);
    }

    /** Whether this is one of the four notices that affect attendance. */
    public boolean isHandled() {
        return ROOM_STARTED.equals(event) || ROOM_FINISHED.equals(event)
                || PARTICIPANT_JOINED.equals(event) || PARTICIPANT_LEFT.equals(event);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
