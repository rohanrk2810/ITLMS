package com.itilms.liveclass.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.SessionCancelledEvent;
import com.itilms.common.event.SessionScheduledEvent;
import com.itilms.liveclass.service.LiveClassService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps live rooms in step with the timetable.
 *
 * <p>Rooms are created when a class is scheduled rather than when the first
 * person clicks Join. By class time the record exists, the pre-provisioning job
 * has created the room on LiveKit, and the first join is a token and nothing
 * else - which matters at 10:00 when sixty people click at once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimetableEventsConsumer {

    private final LiveClassService liveClassService;

    @KafkaListener(topics = {KafkaTopics.SESSION_SCHEDULED, KafkaTopics.SESSION_RESCHEDULED},
            groupId = "liveclass-service")
    public void onScheduled(SessionScheduledEvent event) {
        log.debug("Timetable {} for session {} ({})",
                event.rescheduled() ? "change" : "entry", event.sessionId(), event.mode());
        liveClassService.applySchedule(event);
    }

    @KafkaListener(topics = KafkaTopics.SESSION_CANCELLED, groupId = "liveclass-service")
    public void onCancelled(SessionCancelledEvent event) {
        log.info("Session {} of batch {} cancelled; closing any live room", event.sessionId(), event.batchCode());
        liveClassService.cancelForClassSession(event.sessionId(), event.reason());
    }
}
