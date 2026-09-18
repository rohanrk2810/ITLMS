package com.itilms.reporting.specification;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

import com.itilms.reporting.dto.AuditLogFilter;
import com.itilms.reporting.entity.AuditLog;

/** Optional, composable filters for the audit trail. */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> build(AuditLogFilter filter) {
        return Specification.allOf(
                hasServiceName(filter.serviceName()),
                hasAction(filter.action()),
                hasEntityType(filter.entityType()),
                hasActor(filter.actorUserId()),
                occurredBetween(filter.from(), filter.to()));
    }

    public static Specification<AuditLog> hasServiceName(String serviceName) {
        return blank(serviceName) ? null : (root, query, cb) -> cb.equal(root.get("serviceName"), serviceName.trim());
    }

    public static Specification<AuditLog> hasAction(String action) {
        return blank(action) ? null : (root, query, cb) -> cb.equal(root.get("action"), action.trim());
    }

    public static Specification<AuditLog> hasEntityType(String entityType) {
        return blank(entityType) ? null : (root, query, cb) -> cb.equal(root.get("entityType"), entityType.trim());
    }

    public static Specification<AuditLog> hasActor(Long actorUserId) {
        return actorUserId == null ? null : (root, query, cb) -> cb.equal(root.get("actorUserId"), actorUserId);
    }

    public static Specification<AuditLog> occurredBetween(Instant from, Instant to) {
        if (from == null && to == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("occurredAt"), from, to);
            }
            return from != null
                    ? cb.greaterThanOrEqualTo(root.get("occurredAt"), from)
                    : cb.lessThanOrEqualTo(root.get("occurredAt"), to);
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
