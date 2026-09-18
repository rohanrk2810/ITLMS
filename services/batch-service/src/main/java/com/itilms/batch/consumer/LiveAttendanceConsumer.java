package com.itilms.batch.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.itilms.batch.service.AttendanceService;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.LiveAttendanceComputedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a finished live class into attendance records.
 *
 * <p>This is the join between the two halves of the live-class feature:
 * liveclass-service knows who was in the room and for how long, batch-service
 * owns the register. Sending the computed result as an event rather than having
 * liveclass-service write attendance directly keeps one service in charge of the
 * attendance rules — the thresholds, the roster check, the manual-override
 * precedence all live in one place.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveAttendanceConsumer {

    private final AttendanceService attendanceService;

    @KafkaListener(topics = KafkaTopics.LIVE_ATTENDANCE_COMPUTED, groupId = "batch-service")
    public void onLiveAttendanceComputed(LiveAttendanceComputedEvent event) {
        log.info("Live attendance for session {}: {} participant record(s)",
                event.classSessionId(), event.entries().size());
        attendanceService.applyLiveAttendance(event);
    }
}
