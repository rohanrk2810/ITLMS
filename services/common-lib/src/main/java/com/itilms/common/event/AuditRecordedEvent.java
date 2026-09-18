package com.itilms.common.event;

import java.time.Instant;

/**
 * One entry for the institute-wide audit trail (Doc S5, S12, S14).
 *
 * <p>Audit is published rather than written locally for a specific reason: in a
 * database-per-service system there is no single table to write to, and an
 * auditor needs one chronological view across all twelve services. Every
 * service emits here; reporting-service owns the store and the query API.
 *
 * <p>{@code oldValue} and {@code newValue} hold JSON snapshots. Producers must
 * strip passwords, tokens and card data before publishing (Doc S12).
 */
public record AuditRecordedEvent(
        String eventId,
        Instant occurredAt,
        String serviceName,
        Long actorUserId,
        String actorEmail,
        String actorRole,
        String action,
        String entityType,
        String entityId,
        String oldValue,
        String newValue,
        String ipAddress,
        String userAgent
) implements DomainEvent {
}
