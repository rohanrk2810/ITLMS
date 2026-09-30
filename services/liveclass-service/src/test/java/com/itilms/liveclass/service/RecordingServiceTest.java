package com.itilms.liveclass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.liveclass.config.LiveKitProperties;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.LiveSessionStatus;
import com.itilms.liveclass.livekit.LiveKitGateway;
import com.itilms.liveclass.repository.LiveSessionRepository;

class RecordingServiceTest {

    private LiveSessionRepository sessions;
    private HostAccess hostAccess;
    private LiveKitGateway liveKit;
    private RecordingService service;
    private LiveSession session;

    @BeforeEach
    void setUp() {
        sessions = mock(LiveSessionRepository.class);
        hostAccess = mock(HostAccess.class);
        liveKit = mock(LiveKitGateway.class);
        LiveKitProperties props = new LiveKitProperties();
        props.setApiKey("k");
        props.setApiSecret("s");
        props.setRecordingStoragePath("/recordings");
        service = new RecordingService(sessions, hostAccess, liveKit, props, mock(EventPublisher.class));

        session = new LiveSession();
        session.setId(1L);
        session.setRoomName("room-1");
        session.setStatus(LiveSessionStatus.LIVE);
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
    }

    @Test
    void startAsksLiveKitAndRecordsTheEgressId() {
        when(liveKit.startRoomCompositeEgress(anyString(), anyString())).thenReturn("EG_1");

        service.start(1L);

        assertThat(session.getEgressId()).isEqualTo("EG_1");
        assertThat(session.isRecordingEnabled()).isTrue();
        assertThat(session.getRecordingFilePath()).startsWith("/recordings/room-1/").endsWith(".mp4");
        verify(sessions).save(session);
    }

    @Test
    void cannotStartOnAClassThatIsNotLive() {
        session.setStatus(LiveSessionStatus.SCHEDULED);
        assertThatThrownBy(() -> service.start(1L)).isInstanceOf(BusinessRuleException.class);
        verify(liveKit, never()).startRoomCompositeEgress(anyString(), anyString());
    }

    @Test
    void cannotStartASecondCaptureOnTopOfOne() {
        session.setEgressId("EG_1");
        assertThatThrownBy(() -> service.start(1L)).isInstanceOf(BusinessRuleException.class);
        verify(liveKit, never()).startRoomCompositeEgress(anyString(), anyString());
    }

    @Test
    void stopAsksLiveKitButLeavesTheUrlForTheWebhookToSet() {
        session.setEgressId("EG_1");
        service.stop(1L);
        verify(liveKit).stopEgressQuietly("EG_1");
        assertThat(session.getRecordingUrl()).isNull();
    }

    @Test
    void cannotStopWhenNothingIsRecording() {
        assertThatThrownBy(() -> service.stop(1L)).isInstanceOf(BusinessRuleException.class);
        verify(liveKit, never()).stopEgressQuietly(anyString());
    }

    @Test
    void someoneWhoIsNotAHostCannotStartOrStop() {
        doThrow(new ForbiddenOperationException("no")).when(hostAccess).requireHostOf(session);
        assertThatThrownBy(() -> service.start(1L)).isInstanceOf(ForbiddenOperationException.class);
        verify(liveKit, never()).startRoomCompositeEgress(anyString(), anyString());
    }

    @Test
    void fileForFailsCleanlyWhenThereIsNoRecording() {
        assertThatThrownBy(() -> service.fileFor(1L)).isInstanceOf(ResourceNotFoundException.class);

        session.setRecordingUrl("/api/liveclass/sessions/1/recording");
        session.setRecordingFilePath("Z:/definitely/does/not/exist.mp4");
        assertThatThrownBy(() -> service.fileFor(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void fileForChecksHostOrEnrolledNotJustHost() {
        session.setRecordingUrl("/api/liveclass/sessions/1/recording");
        session.setRecordingFilePath("Z:/nope.mp4");
        assertThatThrownBy(() -> service.fileFor(1L)).isInstanceOf(ResourceNotFoundException.class);
        verify(hostAccess).requireHostOrEnrolled(session);
        verify(hostAccess, never()).requireHostOf(any());
    }
}
