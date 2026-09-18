package com.itilms.common.event;

import java.time.Instant;

/**
 * Published by identity-service whenever an account is provisioned.
 *
 * <p>Also reused on {@code USER_UPDATED} to carry the refreshed display data.
 *
 * @param selfRegistered true only when the person created the account themselves
 *                       through the public sign-up form. admission-service
 *                       creates a student profile for those accounts and no
 *                       others. Staff-created accounts already have a profile:
 *                       the admission request that asked for the account made
 *                       it. Deciding from this flag rather than by checking the
 *                       database is deliberate. That check races the admission
 *                       transaction, which has not committed yet when this
 *                       event arrives, and whichever insert lost the race
 *                       failed - sometimes the admin's own "create student".
 */
public record UserCreatedEvent(
        String eventId,
        Instant occurredAt,
        Long userId,
        String email,
        String phone,
        String fullName,
        String role,
        boolean selfRegistered
) implements DomainEvent {

    /** An account created by staff, or by another service on staff's behalf. */
    public static UserCreatedEvent of(Long userId, String email, String phone, String fullName, String role) {
        return new UserCreatedEvent(DomainEvent.newId(), Instant.now(), userId, email, phone, fullName, role, false);
    }

    /** An account the person created through public sign-up. */
    public static UserCreatedEvent selfRegistered(Long userId, String email, String phone, String fullName, String role) {
        return new UserCreatedEvent(DomainEvent.newId(), Instant.now(), userId, email, phone, fullName, role, true);
    }
}
