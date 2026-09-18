package com.itilms.common.event;

import java.time.Instant;

/**
 * Someone joined or left a live class room.
 *
 * <p>Fed by LiveKit webhooks. These raw events are what make attendance for an
 * online batch trustworthy: the institute is not taking the trainer's word for
 * who was present, it has the join and leave timestamps from the media server.
 *
 * @param identity LiveKit participant identity, which IT-ILMS sets to
 *                 {@code user-<userId>} when minting the join token
 */
public record LiveParticipantEvent(
        String eventId,
        Instant occurredAt,
        Long liveSessionId,
        Long classSessionId,
        Long batchId,
        String roomName,
        String identity,
        Long userId,
        Long studentId,
        String role,
        boolean joined,
        Instant joinedAt,
        Instant leftAt
) implements DomainEvent {
}
