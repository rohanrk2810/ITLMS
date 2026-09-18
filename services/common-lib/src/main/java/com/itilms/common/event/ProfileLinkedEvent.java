package com.itilms.common.event;

import java.time.Instant;

/**
 * A domain profile was attached to a user account.
 *
 * <p>Solves a real problem in this decomposition: the access token has to carry
 * the caller's student or trainer id, because every ownership check downstream
 * needs it — but identity-service does not own those profiles, admission-service
 * does.
 *
 * <p>Calling admission-service during login would be the obvious fix and the
 * wrong one: it would make signing in fail whenever admission-service is
 * restarting, and add a network hop to the most latency-sensitive request in the
 * system. Instead admission-service announces the link once, identity-service
 * keeps a copy on the user row, and login reads it locally.
 *
 * <p>The copy is eventually consistent. For the seconds between a profile being
 * created and this event arriving, the user simply has no profile id in their
 * token — which is exactly true, since their profile did not exist when they
 * signed in. They pick it up on their next login or token refresh.
 *
 * @param profileType STUDENT or TRAINER
 */
public record ProfileLinkedEvent(
        String eventId,
        Instant occurredAt,
        Long userId,
        String profileType,
        Long profileId,
        String profileCode
) implements DomainEvent {

    public static ProfileLinkedEvent of(Long userId, String profileType, Long profileId, String profileCode) {
        return new ProfileLinkedEvent(DomainEvent.newId(), Instant.now(),
                userId, profileType, profileId, profileCode);
    }
}
