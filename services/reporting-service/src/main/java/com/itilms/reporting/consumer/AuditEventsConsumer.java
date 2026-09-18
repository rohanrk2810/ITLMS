package com.itilms.reporting.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.AuditRecordedEvent;
import com.itilms.common.event.KafkaTopics;
import com.itilms.reporting.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

/** The one chronological audit trail across all twelve services (Doc S5, S12). */
@Component
@RequiredArgsConstructor
public class AuditEventsConsumer {

    private static final String GROUP = "reporting-service";

    private final AuditLogRepository repository;

    @KafkaListener(topics = KafkaTopics.AUDIT_RECORDED, groupId = GROUP)
    @Transactional
    public void onAuditRecorded(AuditRecordedEvent event) {
        repository.insertIfAbsent(event.eventId(), event.occurredAt(), event.serviceName(),
                event.actorUserId(), event.actorEmail(), event.actorRole(), event.action(),
                event.entityType(), event.entityId(), event.oldValue(), event.newValue(),
                event.ipAddress(), event.userAgent());
    }
}
