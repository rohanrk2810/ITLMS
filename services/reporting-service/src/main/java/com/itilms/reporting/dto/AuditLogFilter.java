package com.itilms.reporting.dto;

import java.time.Instant;

/** Every field optional; an empty filter is "everything", capped by {@code max-export-rows} on export. */
public record AuditLogFilter(
        String serviceName,
        String action,
        String entityType,
        Long actorUserId,
        Instant from,
        Instant to
) {
}
