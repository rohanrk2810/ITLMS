package com.itilms.common.event;

import java.time.Instant;

/**
 * Marker for everything published onto Kafka.
 *
 * <p>Two guarantees every event must keep:
 * <ul>
 *   <li>{@code eventId} is unique, so consumers can de-duplicate. Kafka delivers
 *       at-least-once, and a retried delivery must not send a second SMS or
 *       mark attendance twice.</li>
 *   <li>{@code occurredAt} is when the fact happened, not when it was consumed.</li>
 * </ul>
 *
 * <p>Events are facts in the past tense. They never carry commands, and a
 * consumer failing to process one must never block the producer's transaction.
 */
public interface DomainEvent {

    String eventId();

    Instant occurredAt();

    static String newId() {
        return java.util.UUID.randomUUID().toString();
    }
}
