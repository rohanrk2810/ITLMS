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
 * @param egressId     set only on an {@code egress_*} event
 * @param egressStatus the capture's status (e.g. {@code EGRESS_COMPLETE}), set only on an {@code egress_*} event
 */
public record WebhookNotice(
        String id,
        String event,
        String roomName,
        String roomSid,
        String identity,
        String participantSid,
        Instant occurredAt,
        String egressId,
        String egressStatus
) {

    public static final String ROOM_STARTED = "room_started";
    public static final String ROOM_FINISHED = "room_finished";
    public static final String PARTICIPANT_JOINED = "participant_joined";
    public static final String PARTICIPANT_LEFT = "participant_left";
    public static final String EGRESS_ENDED = "egress_ended";

    public static WebhookNotice from(LivekitWebhook.WebhookEvent event) {
        String roomName = event.hasRoom() ? blankToNull(event.getRoom().getName()) : null;
        String roomSid = event.hasRoom() ? blankToNull(event.getRoom().getSid()) : null;
        String identity = event.hasParticipant() ? blankToNull(event.getParticipant().getIdentity()) : null;
        String participantSid = event.hasParticipant() ? blankToNull(event.getParticipant().getSid()) : null;
        Instant at = event.getCreatedAt() > 0 ? Instant.ofEpochSecond(event.getCreatedAt()) : Instant.now();

        String egressId = null;
        String egressStatus = null;
        if (event.hasEgressInfo()) {
            egressId = blankToNull(event.getEgressInfo().getEgressId());
            egressStatus = event.getEgressInfo().getStatus().name();
            // Egress events carry the room by name only, not the room object other notices carry.
            if (roomName == null) {
                roomName = blankToNull(event.getEgressInfo().getRoomName());
            }
        }

        return new WebhookNotice(blankToNull(event.getId()), event.getEvent(),
                roomName, roomSid, identity, participantSid, at, egressId, egressStatus);
    }

    /** Whether this is one of the notices this service acts on. */
    public boolean isHandled() {
        return ROOM_STARTED.equals(event) || ROOM_FINISHED.equals(event)
                || PARTICIPANT_JOINED.equals(event) || PARTICIPANT_LEFT.equals(event)
                || EGRESS_ENDED.equals(event);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
