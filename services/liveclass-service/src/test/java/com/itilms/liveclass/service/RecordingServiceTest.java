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

    // ---- recording from the host's browser ----

    private void useTempStorage(java.nio.file.Path dir, long maxBytes) {
        LiveKitProperties props = new LiveKitProperties();
        props.setApiKey("k");
        props.setApiSecret("s");
        props.setRecordingStoragePath(dir.toString());
        props.setBrowserRecordingMaxBytes(maxBytes);
        service = new RecordingService(sessions, hostAccess, liveKit, props, mock(EventPublisher.class));
        when(sessions.findByIdForUpdate(1L)).thenReturn(Optional.of(session));
    }

    @Test
    void browserRecordingStartsAppendsInOrderAndFinishesWithAFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        useTempStorage(dir, 1024);

        service.startBrowser(1L);
        assertThat(session.isBrowserRecordingActive()).isTrue();
        assertThat(session.getRecordingFilePath()).endsWith(".webm");

        service.appendChunk(1L, 0, new byte[] {1, 2, 3});
        var state = service.appendChunk(1L, 1, new byte[] {4, 5});
        assertThat(state.nextChunk()).isEqualTo(2);
        assertThat(state.bytes()).isEqualTo(5);

        service.stopBrowser(1L);
        assertThat(session.isBrowserRecordingActive()).isFalse();
        assertThat(session.getRecordingUrl()).isNotNull();
        assertThat(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(session.getRecordingFilePath())))
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void aRetriedPieceIsAcknowledgedButNotWrittenTwice(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        useTempStorage(dir, 1024);
        service.startBrowser(1L);
        service.appendChunk(1L, 0, new byte[] {1, 2});

        var state = service.appendChunk(1L, 0, new byte[] {1, 2});

        assertThat(state.nextChunk()).isEqualTo(1);
        assertThat(java.nio.file.Files.size(java.nio.file.Path.of(session.getRecordingFilePath()))).isEqualTo(2);
    }

    @Test
    void aPieceThatSkipsAheadIsRefused(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        useTempStorage(dir, 1024);
        service.startBrowser(1L);
        assertThatThrownBy(() -> service.appendChunk(1L, 3, new byte[] {1}))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "CHUNK_OUT_OF_ORDER");
    }

    @Test
    void anEmptyPieceAndAnOversizedRecordingAreRefused(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        useTempStorage(dir, 4);
        service.startBrowser(1L);
        assertThatThrownBy(() -> service.appendChunk(1L, 0, new byte[0])).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.appendChunk(1L, 0, new byte[5]))
                .hasFieldOrPropertyWithValue("code", "RECORDING_TOO_LARGE");
    }

    @Test
    void stoppingWithNothingReceivedLeavesNoRecording(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        useTempStorage(dir, 1024);
        service.startBrowser(1L);
        service.stopBrowser(1L);
        assertThat(session.getRecordingUrl()).isNull();
        assertThat(session.getRecordingFilePath()).isNull();
    }

    @Test
    void browserRecordingCannotStartOverAnotherCaptureOrAClassThatIsNotLive(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        useTempStorage(dir, 1024);
        session.setEgressId("EG_1");
        assertThatThrownBy(() -> service.startBrowser(1L)).hasFieldOrPropertyWithValue("code", "ALREADY_RECORDING");

        session.setEgressId(null);
        service.startBrowser(1L);
        assertThatThrownBy(() -> service.startBrowser(1L)).hasFieldOrPropertyWithValue("code", "ALREADY_RECORDING");
        assertThatThrownBy(() -> service.start(1L)).hasFieldOrPropertyWithValue("code", "ALREADY_RECORDING");

        session.finishBrowserRecording();
        session.setStatus(LiveSessionStatus.SCHEDULED);
        assertThatThrownBy(() -> service.startBrowser(1L)).hasFieldOrPropertyWithValue("code", "CLASS_NOT_LIVE");
    }

    @Test
    void deleteRemovesTheFileAndForgetsTheRecording(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        useTempStorage(dir, 1024);
        service.startBrowser(1L);
        service.appendChunk(1L, 0, new byte[] {1, 2, 3});
        service.stopBrowser(1L);
        java.nio.file.Path file = java.nio.file.Path.of(session.getRecordingFilePath());
        assertThat(file).exists();

        service.delete(1L);

        assertThat(file).doesNotExist();
        assertThat(file.getParent()).doesNotExist();
        assertThat(session.getRecordingUrl()).isNull();
        assertThat(session.getRecordingFilePath()).isNull();
        assertThat(session.getRecordingBytes()).isZero();
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteIsRefusedWhileARecordingIsRunning(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        useTempStorage(dir, 1024);
        service.startBrowser(1L);
        assertThatThrownBy(() -> service.delete(1L)).hasFieldOrPropertyWithValue("code", "RECORDING_IN_PROGRESS");
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
