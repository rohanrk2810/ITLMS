package com.itilms.liveclass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.itilms.common.event.EventPublisher;
import com.itilms.liveclass.config.LiveClassProperties;
import com.itilms.liveclass.entity.LiveParticipant;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.LiveSessionStatus;
import com.itilms.liveclass.entity.ParticipantRole;
import com.itilms.liveclass.livekit.WebhookNotice;
import com.itilms.liveclass.repository.LiveParticipantEventRepository;
import com.itilms.liveclass.repository.LiveParticipantRepository;
import com.itilms.liveclass.repository.LiveSessionRepository;

/**
 * When a closed room ends a class, and when it does not.
 *
 * <p>LiveKit closes rooms for reasons that have nothing to do with the lecture
 * being over. Each test here is a closure that once, or would once, have locked
 * students out of a class that was still running.
 */
@ExtendWith(MockitoExtension.class)
class LiveKitWebhookHandlerTest {

    private static final String ROOM = "itilms-session-42";
    /** A 10:00-11:00 class. */
    private static final Instant START = Instant.parse("2026-01-12T04:30:00Z");
    private static final Instant END = START.plus(Duration.ofHours(1));

    @Mock
    private LiveSessionRepository sessionRepository;
    @Mock
    private LiveParticipantRepository participantRepository;
    @Mock
    private LiveParticipantEventRepository eventRepository;
    @Mock
    private LiveAttendanceService attendanceService;
    @Mock
    private EventPublisher events;

    private LiveKitWebhookHandler handler;
    private LiveSession session;

    @BeforeEach
    void setUp() {
        LiveClassProperties props = new LiveClassProperties();
        props.setFinishMarginMinutes(15);
        handler = new LiveKitWebhookHandler(sessionRepository, participantRepository, eventRepository,
                attendanceService, events, props);

        session = LiveSession.builder()
                .id(7L).classSessionId(42L).batchId(3L).roomName(ROOM)
                .scheduledStartAt(START).scheduledEndAt(END)
                .livekitRoomSid("RM_abc")
                .build();
        when(sessionRepository.findByRoomNameForUpdate(ROOM)).thenReturn(Optional.of(session));
    }

    private static WebhookNotice notice(String id, String event, String identity, Instant at) {
        return new WebhookNotice(id, event, ROOM, "RM_abc", identity, null, at, null, null);
    }

    private static Instant at(int minutesFromStart) {
        return START.plus(Duration.ofMinutes(minutesFromStart));
    }

    @Test
    @DisplayName("A pre-created room closing before anyone came does not settle the class")
    void unusedRoomClosingEarly() {
        handler.handle(notice("EV_1", WebhookNotice.ROOM_FINISHED, null, at(-20)));

        verify(attendanceService, never()).settle(anyLong(), any());
        assertThat(session.getLivekitRoomSid()).as("forgotten so the next join recreates it").isNull();
        assertThat(session.isAttendanceComputed()).isFalse();
    }

    @Test
    @DisplayName("Everyone dropping out mid-class does not settle the class")
    void outageMidClass() {
        session.setStatus(LiveSessionStatus.LIVE);

        handler.handle(notice("EV_2", WebhookNotice.ROOM_FINISHED, null, at(25)));

        verify(attendanceService, never()).settle(anyLong(), any());
        assertThat(session.getLivekitRoomSid()).isNull();
    }

    @Test
    @DisplayName("A running class whose room closes near the end is settled")
    void closesNearEnd() {
        session.setStatus(LiveSessionStatus.LIVE);

        handler.handle(notice("EV_3", WebhookNotice.ROOM_FINISHED, null, at(50)));

        verify(attendanceService).settle(eq(7L), isNull());
    }

    @Test
    @DisplayName("A redelivered webhook is ignored")
    void duplicateIgnored() {
        when(eventRepository.existsByLivekitEventId("EV_4")).thenReturn(true);

        handler.handle(notice("EV_4", WebhookNotice.PARTICIPANT_JOINED, "user-9", at(0)));

        verify(participantRepository, never()).findByLiveSessionIdAndIdentity(anyLong(), anyString());
    }

    @Test
    @DisplayName("Leave notices arriving after settlement do not change the totals")
    void frozenAfterSettlement() {
        session.setStatus(LiveSessionStatus.ENDED);
        session.setAttendanceComputed(true);
        session.setAttendanceComputedAt(END);

        handler.handle(notice("EV_5", WebhookNotice.PARTICIPANT_LEFT, "user-9", at(61)));

        verify(participantRepository, never()).findByLiveSessionIdAndIdentity(anyLong(), anyString());
    }

    @Test
    @DisplayName("The trainer's first entry tells the batch the class is live; a student's does not")
    void notifiesOnTrainerArrival() {
        LiveParticipant trainer = LiveParticipant.builder().id(1L).liveSessionId(7L)
                .identity("user-5").userId(5L).role(ParticipantRole.TRAINER).build();
        LiveParticipant student = LiveParticipant.builder().id(2L).liveSessionId(7L)
                .identity("user-9").userId(9L).studentId(90L).role(ParticipantRole.STUDENT).build();
        when(participantRepository.findByLiveSessionIdAndIdentity(7L, "user-5")).thenReturn(Optional.of(trainer));
        when(participantRepository.findByLiveSessionIdAndIdentity(7L, "user-9")).thenReturn(Optional.of(student));

        handler.handle(notice("EV_6", WebhookNotice.PARTICIPANT_JOINED, "user-9", at(-2)));
        verify(events, never()).notifyBatch(anyLong(), anyString(), anyString(), anyString(), anyString());

        handler.handle(notice("EV_7", WebhookNotice.PARTICIPANT_JOINED, "user-5", at(0)));
        verify(events).notifyBatch(eq(3L), eq("LIVE_CLASS"), anyString(), anyString(), eq("/live/42"));
        assertThat(session.getStatus()).isEqualTo(LiveSessionStatus.LIVE);
    }

    @Test
    @DisplayName("A completed capture clears the egress id and publishes the recording")
    void egressCompleted() {
        session.setEgressId("EG_1");
        session.setRecordingFilePath("/recordings/itilms-session-42/123.mp4");

        handler.handle(egressNotice("EV_8", "EG_1", "EGRESS_COMPLETE"));

        assertThat(session.getEgressId()).isNull();
        assertThat(session.getRecordingUrl()).isEqualTo("/api/liveclass/sessions/7/recording");
    }

    @Test
    @DisplayName("A failed capture clears the egress id but publishes no recording")
    void egressFailed() {
        session.setEgressId("EG_1");
        session.setRecordingFilePath("/recordings/itilms-session-42/123.mp4");

        handler.handle(egressNotice("EV_9", "EG_1", "EGRESS_FAILED"));

        assertThat(session.getEgressId()).isNull();
        assertThat(session.getRecordingUrl()).isNull();
    }

    @Test
    @DisplayName("egress_ended for a capture that was superseded is ignored")
    void egressEndedForAStaleCapture() {
        session.setEgressId("EG_2");
        session.setRecordingFilePath("/recordings/itilms-session-42/999.mp4");

        handler.handle(egressNotice("EV_10", "EG_1", "EGRESS_COMPLETE"));

        assertThat(session.getEgressId()).isEqualTo("EG_2");
        assertThat(session.getRecordingUrl()).isNull();
    }

    private static WebhookNotice egressNotice(String id, String egressId, String egressStatus) {
        return new WebhookNotice(id, WebhookNotice.EGRESS_ENDED, ROOM, null, null, null, at(30), egressId, egressStatus);
    }
}
