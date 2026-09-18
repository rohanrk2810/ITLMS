package com.itilms.common.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The generic channel every service uses to ask for a notification.
 *
 * <p>Producers say <em>who</em> and <em>what</em>; notification-service decides
 * <em>how</em> — in-app row, email, or both — based on the event type table in
 * Doc Section 16. Keeping that decision in one service means changing "fee
 * overdue now also emails the parent" is a one-service change.
 *
 * <p>A request may address users directly ({@code recipientUserIds}), or by
 * audience ({@code batchId} / {@code role}), which notification-service expands.
 */
public record NotificationRequestedEvent(
        String eventId,
        Instant occurredAt,
        List<Long> recipientUserIds,
        Long batchId,
        String role,
        String type,
        String title,
        String message,
        String actionUrl,
        boolean email,
        Map<String, String> metadata
) implements DomainEvent {

    /** Addressed to specific users, in-app only. */
    public static NotificationRequestedEvent toUsers(List<Long> userIds, String type,
                                                     String title, String message, String actionUrl) {
        return new NotificationRequestedEvent(DomainEvent.newId(), Instant.now(),
                userIds, null, null, type, title, message, actionUrl, false, Map.of());
    }

    /** Addressed to specific users, in-app plus email. */
    public static NotificationRequestedEvent toUsersWithEmail(List<Long> userIds, String type,
                                                              String title, String message, String actionUrl) {
        return new NotificationRequestedEvent(DomainEvent.newId(), Instant.now(),
                userIds, null, null, type, title, message, actionUrl, true, Map.of());
    }

    /** Addressed to everyone enrolled in a batch; the recipient list is resolved downstream. */
    /**
     * Addressed to everyone holding a role - "finance" rather than a list of
     * names, so a new accountant starts receiving overdue alerts without anyone
     * editing a distribution list.
     */
    public static NotificationRequestedEvent toRole(String role, String type,
                                                    String title, String message, String actionUrl) {
        return new NotificationRequestedEvent(DomainEvent.newId(), Instant.now(),
                List.of(), null, role, type, title, message, actionUrl, false, Map.of());
    }

    public static NotificationRequestedEvent toBatch(Long batchId, String type,
                                                     String title, String message, String actionUrl) {
        return new NotificationRequestedEvent(DomainEvent.newId(), Instant.now(),
                List.of(), batchId, null, type, title, message, actionUrl, false, Map.of());
    }
}
