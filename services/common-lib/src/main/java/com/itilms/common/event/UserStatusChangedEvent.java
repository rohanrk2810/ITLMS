package com.itilms.common.event;

import java.time.Instant;

/**
 * Account activated, deactivated or blocked.
 *
 * <p>Consumers use this to stop showing a dropped student in batch rosters
 * without querying identity-service on every page load.
 */
public record UserStatusChangedEvent(
        String eventId,
        Instant occurredAt,
        Long userId,
        String email,
        String previousStatus,
        String newStatus,
        Long changedByUserId
) implements DomainEvent {

    public static UserStatusChangedEvent of(Long userId, String email, String previousStatus,
                                            String newStatus, Long changedByUserId) {
        return new UserStatusChangedEvent(DomainEvent.newId(), Instant.now(),
                userId, email, previousStatus, newStatus, changedByUserId);
    }
}
