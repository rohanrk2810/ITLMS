package com.itilms.common.event;

import java.util.List;
import java.util.Map;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.common.security.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The one way services put events on Kafka.
 *
 * <p>Publishing is deferred until after the database transaction commits. That
 * ordering matters: if a service published "payment recorded" and its own
 * transaction then rolled back, notification-service would email a receipt for
 * money the ledger never took. {@link #publishAfterCommit} routes through a
 * Spring application event so the Kafka send happens only on commit, while
 * {@link #publishNow} is available for the rare case with no transaction to wait
 * for, such as replaying a webhook.
 *
 * <p>Kafka failures are logged, not rethrown. A notification that did not go out
 * must never roll back the attendance that was already saved; the event carries
 * enough information to be replayed from the source record if needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    /** Queues the event; it reaches Kafka only if the current transaction commits. */
    public void publishAfterCommit(String topic, String key, DomainEvent event) {
        applicationEventPublisher.publishEvent(new PendingEvent(topic, key, event));
    }

    public void publishAfterCommit(String topic, DomainEvent event) {
        publishAfterCommit(topic, event.eventId(), event);
    }

    /** Sends immediately, without waiting for a transaction. */
    public void publishNow(String topic, String key, DomainEvent event) {
        send(topic, key, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onCommit(PendingEvent pending) {
        send(pending.topic(), pending.key(), pending.event());
    }

    private void send(String topic, String key, DomainEvent event) {
        try {
            kafkaTemplate.send(topic, key, event)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            log.error("Failed to publish {} to {} (eventId={})",
                                    event.getClass().getSimpleName(), topic, event.eventId(), failure);
                        } else {
                            log.debug("Published {} to {} (eventId={})",
                                    event.getClass().getSimpleName(), topic, event.eventId());
                        }
                    });
        } catch (Exception ex) {
            log.error("Could not hand {} to Kafka on topic {}", event.getClass().getSimpleName(), topic, ex);
        }
    }

    // ---------------------------------------------------------------------
    // Convenience helpers used by almost every service
    // ---------------------------------------------------------------------

    public void notifyUsers(List<Long> userIds, String type, String title, String message, String actionUrl) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        publishAfterCommit(KafkaTopics.NOTIFICATION_REQUESTED,
                NotificationRequestedEvent.toUsers(userIds, type, title, message, actionUrl));
    }

    public void notifyRole(String role, String type, String title, String message, String actionUrl) {
        publishAfterCommit(KafkaTopics.NOTIFICATION_REQUESTED,
                NotificationRequestedEvent.toRole(role, type, title, message, actionUrl));
    }

    public void notifyBatch(Long batchId, String type, String title, String message, String actionUrl) {
        publishAfterCommit(KafkaTopics.NOTIFICATION_REQUESTED,
                NotificationRequestedEvent.toBatch(batchId, type, title, message, actionUrl));
    }

    /**
     * Records an administrative action against the audit trail, attributing it to
     * the caller in the current security context.
     */
    public void audit(String serviceName, String action, String entityType, Object entityId,
                      Object oldValue, Object newValue) {
        var principal = SecurityUtils.currentPrincipal().orElse(null);
        var event = new AuditRecordedEvent(
                DomainEvent.newId(), java.time.Instant.now(), serviceName,
                principal == null ? null : principal.userId(),
                principal == null ? "system" : principal.email(),
                principal == null ? "SYSTEM" : principal.role(),
                action, entityType, entityId == null ? null : String.valueOf(entityId),
                toJson(oldValue), toJson(newValue), null, null);
        publishAfterCommit(KafkaTopics.AUDIT_RECORDED, event);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("Could not serialise audit payload of type {}", value.getClass().getSimpleName());
            return Map.of("unserializable", value.getClass().getSimpleName()).toString();
        }
    }

    /** Internal carrier between the service call and the post-commit send. */
    record PendingEvent(String topic, String key, DomainEvent event) {
    }
}
