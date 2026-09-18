package com.itilms.reporting.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.reporting.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /**
     * Idempotent write: Kafka delivers at-least-once, and {@code ON CONFLICT DO
     * NOTHING} is what makes a redelivered {@code AuditRecordedEvent} a no-op
     * instead of a second row. A native insert is used rather than
     * {@code save()} so a duplicate never raises an exception in the first
     * place - once the JDBC driver has seen one, the whole transaction is
     * unusable until it is rolled back.
     */
    @Modifying
    @Query(value = """
            INSERT INTO audit_logs (event_id, occurred_at, service_name, actor_user_id, actor_email, actor_role,
                                     action, entity_type, entity_id, old_value, new_value, ip_address, user_agent,
                                     recorded_at)
            VALUES (:eventId, :occurredAt, :serviceName, :actorUserId, :actorEmail, :actorRole,
                    :action, :entityType, :entityId, :oldValue, :newValue, :ipAddress, :userAgent, now())
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("eventId") String eventId, @Param("occurredAt") Instant occurredAt,
                        @Param("serviceName") String serviceName, @Param("actorUserId") Long actorUserId,
                        @Param("actorEmail") String actorEmail, @Param("actorRole") String actorRole,
                        @Param("action") String action, @Param("entityType") String entityType,
                        @Param("entityId") String entityId, @Param("oldValue") String oldValue,
                        @Param("newValue") String newValue, @Param("ipAddress") String ipAddress,
                        @Param("userAgent") String userAgent);
}
