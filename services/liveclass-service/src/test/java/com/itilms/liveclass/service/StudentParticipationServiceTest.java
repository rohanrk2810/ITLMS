package com.itilms.liveclass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.itilms.liveclass.entity.LiveParticipant;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.LiveSessionStatus;
import com.itilms.liveclass.repository.LiveParticipantRepository;
import com.itilms.liveclass.repository.LiveSessionRepository;

class StudentParticipationServiceTest {

    private LiveSessionRepository sessions;
    private LiveParticipantRepository participants;
    private StudentParticipationService service;

    @BeforeEach
    void setUp() {
        sessions = mock(LiveSessionRepository.class);
        participants = mock(LiveParticipantRepository.class);
        service = new StudentParticipationService(sessions, participants);
    }

    private static LiveSession ended(long id) {
        return LiveSession.builder().id(id).batchId(7L).status(LiveSessionStatus.ENDED).build();
    }

    private static LiveParticipant joined(long sessionId, int seconds, Integer percent, String at) {
        return LiveParticipant.builder().liveSessionId(sessionId).studentId(10L).firstJoinedAt(Instant.parse(at))
                .attendedSeconds(seconds).attendancePercent(percent).build();
    }

    @Test
    @DisplayName("Held is finished classes in the student's batches; joined is the ones they were in the room for")
    void heldAndJoined() {
        when(sessions.findByBatchIdInAndStatus(any(), any())).thenReturn(List.of(ended(1), ended(2), ended(3)));
        when(participants.findByStudentId(10L)).thenReturn(List.of(
                joined(1, 3600, 100, "2026-09-01T10:00:00Z"),
                joined(2, 1800, 50, "2026-09-02T10:00:00Z"),
                joined(99, 3600, 100, "2026-09-03T10:00:00Z"),                       // a class of another batch
                LiveParticipant.builder().liveSessionId(3L).studentId(10L).build())); // registered but never entered the room

        var result = service.of(10L, List.of(7L));

        assertThat(result.sessionsHeld()).isEqualTo(3);
        assertThat(result.sessionsJoined()).isEqualTo(2);
        assertThat(result.minutesInRoom()).isEqualTo(90);
        assertThat(result.averageAttendancePercent()).isEqualTo(75);
        assertThat(result.lastJoinedAt()).isEqualTo(Instant.parse("2026-09-02T10:00:00Z"));
    }

    @Test
    @DisplayName("Nothing held yet means nothing missed, and a class the student skipped counts as held but not joined")
    void nothingHeldAndSkipped() {
        when(sessions.findByBatchIdInAndStatus(any(), any())).thenReturn(List.of());
        assertThat(service.of(10L, List.of(7L)).sessionsHeld()).isZero();

        when(sessions.findByBatchIdInAndStatus(any(), any())).thenReturn(List.of(ended(1), ended(2)));
        when(participants.findByStudentId(10L)).thenReturn(List.of());
        var skipped = service.of(10L, List.of(7L));
        assertThat(skipped.sessionsHeld()).isEqualTo(2);
        assertThat(skipped.sessionsJoined()).isZero();
        assertThat(skipped.averageAttendancePercent()).isNull();
    }

    @Test
    @DisplayName("A student in no batch has no live classes, and nothing is queried")
    void noBatches() {
        var result = service.of(10L, List.of());

        assertThat(result.sessionsHeld()).isZero();
        verify(sessions, never()).findByBatchIdInAndStatus(any(), any());
    }
}
