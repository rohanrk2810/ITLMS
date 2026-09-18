package com.itilms.reporting.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.AttendanceMarkedEvent;
import com.itilms.common.event.KafkaTopics;
import com.itilms.reporting.repository.StudentSessionAttendanceRepository;

import lombok.RequiredArgsConstructor;

/**
 * Keeps the per-student attendance the dashboard shows (Doc S15) in step with
 * batch-service, including corrections - see the upsert's own note.
 */
@Component
@RequiredArgsConstructor
public class AttendanceEventsConsumer {

    private static final String GROUP = "reporting-service";

    private final StudentSessionAttendanceRepository repository;

    @KafkaListener(topics = KafkaTopics.ATTENDANCE_MARKED, groupId = GROUP)
    @Transactional
    public void onAttendanceMarked(AttendanceMarkedEvent event) {
        for (AttendanceMarkedEvent.Entry entry : event.entries()) {
            repository.upsert(entry.studentId(), event.sessionId(), event.batchId(),
                    event.sessionDate(), entry.status(), event.occurredAt());
        }
    }
}
