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
        boolean selfRegistered,
        /** Extra duties given by an administrator; absent (null) in events sent before they existed. */
        java.util.List<String> extraRoles
) implements DomainEvent {

    public UserCreatedEvent {
        extraRoles = extraRoles == null ? java.util.List.of() : java.util.List.copyOf(extraRoles);
    }

    /** Without extra duties. */
    public UserCreatedEvent(String eventId, Instant occurredAt, Long userId, String email, String phone,
                            String fullName, String role, boolean selfRegistered) {
        this(eventId, occurredAt, userId, email, phone, fullName, role, selfRegistered, java.util.List.of());
    }


    /** An account created by staff, or by another service on staff's behalf. */
    public static UserCreatedEvent of(Long userId, String email, String phone, String fullName, String role) {
        return new UserCreatedEvent(DomainEvent.newId(), Instant.now(), userId, email, phone, fullName, role, false);
    }

    /** The same facts after an administrator changed the person's extra duties. */
    public static UserCreatedEvent withDuties(Long userId, String email, String phone, String fullName, String role,
                                              java.util.List<String> extraRoles) {
        return new UserCreatedEvent(DomainEvent.newId(), Instant.now(), userId, email, phone, fullName, role, false,
                extraRoles);
    }

    /** An account the person created through public sign-up. */
    public static UserCreatedEvent selfRegistered(Long userId, String email, String phone, String fullName, String role) {
        return new UserCreatedEvent(DomainEvent.newId(), Instant.now(), userId, email, phone, fullName, role, true);
    }
}
