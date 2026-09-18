package com.itilms.reporting.dto;

import java.time.Instant;

import com.itilms.reporting.entity.AuditLog;

public record AuditLogResponse(
        Long id,
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
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getOccurredAt(), log.getServiceName(), log.getActorUserId(),
                log.getActorEmail(), log.getActorRole(), log.getAction(), log.getEntityType(), log.getEntityId(),
                log.getOldValue(), log.getNewValue(), log.getIpAddress(), log.getUserAgent());
    }
}
