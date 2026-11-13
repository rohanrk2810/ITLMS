package com.itilms.liveclass.service;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.liveclass.config.LiveKitProperties;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.LiveSessionStatus;
import com.itilms.liveclass.livekit.LiveKitGateway;
import com.itilms.liveclass.repository.LiveSessionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Recording a class with LiveKit Egress.
 *
 * <p>One capture at a time, started and stopped by the trainer or staff by hand - there is no auto-record, so a
 * class is only ever recorded when someone deliberately turns it on. Egress writes a room-composite file (everyone's
 * video and audio, mixed into one) to a volume this service and the {@code livekit-egress} container both mount; the
 * file becomes visible to callers only once the {@code egress_ended} webhook confirms it is complete, which is what
 * sets {@link LiveSession#getRecordingUrl()}.
 */
@Service
@RequiredArgsConstructor
public class RecordingService {

    private static final String SERVICE_NAME = "liveclass-service";

    private final LiveSessionRepository sessions;
    private final HostAccess hostAccess;
    private final LiveKitGateway liveKit;
    private final LiveKitProperties properties;
    private final EventPublisher events;

    public void start(Long liveSessionId) {
        LiveSession session = requireSession(liveSessionId);
        hostAccess.requireHostOf(session);
        if (session.getStatus() != LiveSessionStatus.LIVE) {
            throw new BusinessRuleException("CLASS_NOT_LIVE", "The class must be running to start recording it.");
        }
        if (session.isBeingRecorded()) {
            throw new BusinessRuleException("ALREADY_RECORDING", "This class is already being recorded.");
        }

        String filepath = filepathFor(session, ".mp4");
        String egressId = liveKit.startRoomCompositeEgress(session.getRoomName(), filepath);
        session.setEgressId(egressId);
        session.setRecordingStartedAt(Instant.now());
        session.setRecordingFilePath(filepath);
        session.setRecordingEnabled(true);
        session.setRecordingUrl(null);
        sessions.save(session);

        events.audit(SERVICE_NAME, "LIVE_RECORDING_STARTED", "LiveSession", liveSessionId, null, Map.of());
    }

    public void stop(Long liveSessionId) {
        LiveSession session = requireSession(liveSessionId);
        hostAccess.requireHostOf(session);
        if (session.getEgressId() == null) {
            throw new BusinessRuleException("NOT_RECORDING", "This class is not being recorded.");
        }

        liveKit.stopEgressQuietly(session.getEgressId());
        events.audit(SERVICE_NAME, "LIVE_RECORDING_STOP_REQUESTED", "LiveSession", liveSessionId, null, Map.of());
        // egressId is cleared, and recordingUrl set, by the egress_ended webhook once Egress finishes writing -
        // stopping is not instant, so nothing here can hand back a file that does not exist yet.
    }

    /** The finished recording, for a host of the class or a student enrolled in it. Null if there is none. */
    public File fileFor(Long liveSessionId) {
        LiveSession session = requireSession(liveSessionId);
        hostAccess.requireHostOrEnrolled(session);
        if (session.getRecordingUrl() == null || session.getRecordingFilePath() == null) {
            throw new ResourceNotFoundException("This class has no recording");
        }
        File file = new File(session.getRecordingFilePath());
        if (!file.isFile()) {
            throw new ResourceNotFoundException("This class has no recording");
        }
        return file;
    }

    // -----------------------------------------------------------------
    // Recording from the host's own browser
    // -----------------------------------------------------------------

    /** What the browser needs to carry on: the next piece number and how much has arrived. */
    public record BrowserRecordingState(int nextChunk, long bytes) {
    }

    /** A recording ready to be sent to a browser, with the name it should be saved under. */
    public record RecordingFile(File file, String contentType, String downloadName) {
    }

    /**
     * Starts a recording that the host's browser sends in pieces. The class must be running, and nothing else may be
     * recording it. Pieces are sent with {@link #appendChunk}, in order, and the recording is closed with
     * {@link #stopBrowser} (or by the class ending).
     */
    @Transactional
    public void startBrowser(Long liveSessionId) {
        LiveSession session = lockSession(liveSessionId);
        hostAccess.requireHostOf(session);
        if (session.getStatus() != LiveSessionStatus.LIVE) {
            throw new BusinessRuleException("CLASS_NOT_LIVE", "The class must be running to start recording it.");
        }
        if (session.isBeingRecorded()) {
            throw new BusinessRuleException("ALREADY_RECORDING", "This class is already being recorded.");
        }

        String filepath = filepathFor(session, ".webm");
        session.startBrowserRecording(filepath, Instant.now());
        sessions.save(session);

        events.audit(SERVICE_NAME, "LIVE_RECORDING_STARTED", "LiveSession", liveSessionId, null,
                Map.of("source", "browser"));
    }

    /**
     * Adds the next piece to the file. Pieces must arrive in order: a piece that was already stored is quietly
     * acknowledged (the browser is retrying), one that skips ahead is refused so the file never has a hole in it.
     */
    @Transactional
    public BrowserRecordingState appendChunk(Long liveSessionId, int sequence, byte[] data) {
        LiveSession session = lockSession(liveSessionId);
        hostAccess.requireHostOf(session);
        if (!session.isBrowserRecordingActive()) {
            throw new BusinessRuleException("NOT_RECORDING", "This class is not being recorded from a browser.");
        }
        if (sequence < session.getRecordingChunks()) {
            return new BrowserRecordingState(session.getRecordingChunks(), session.getRecordingBytes());
        }
        if (sequence > session.getRecordingChunks()) {
            throw new BusinessRuleException("CHUNK_OUT_OF_ORDER",
                    "Expected piece " + session.getRecordingChunks() + " but received " + sequence + ".");
        }
        if (data.length == 0) {
            throw new BusinessRuleException("EMPTY_CHUNK", "That piece was empty.");
        }
        if (session.getRecordingBytes() + data.length > properties.getBrowserRecordingMaxBytes()) {
            throw new BusinessRuleException("RECORDING_TOO_LARGE",
                    "This recording has reached its size limit and cannot grow further.");
        }

        Path file = Path.of(session.getRecordingFilePath());
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not write the recording piece", ex);
        }
        session.setRecordingChunks(sequence + 1);
        session.setRecordingBytes(session.getRecordingBytes() + data.length);
        sessions.save(session);
        return new BrowserRecordingState(session.getRecordingChunks(), session.getRecordingBytes());
    }

    /** Closes a browser recording; what arrived becomes the class's recording. */
    @Transactional
    public BrowserRecordingState stopBrowser(Long liveSessionId) {
        LiveSession session = lockSession(liveSessionId);
        hostAccess.requireHostOf(session);
        if (!session.isBrowserRecordingActive()) {
            throw new BusinessRuleException("NOT_RECORDING", "This class is not being recorded from a browser.");
        }
        session.finishBrowserRecording();
        sessions.save(session);
        events.audit(SERVICE_NAME, "LIVE_RECORDING_STOPPED", "LiveSession", liveSessionId, null,
                Map.of("source", "browser", "bytes", session.getRecordingBytes()));
        return new BrowserRecordingState(session.getRecordingChunks(), session.getRecordingBytes());
    }

    /** The finished recording with its content type and a readable file name, for watching or downloading. */
    public RecordingFile open(Long liveSessionId) {
        File file = fileFor(liveSessionId);
        LiveSession session = requireSession(liveSessionId);
        String name = file.getName();
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".mp4";
        String type = ".webm".equals(extension) ? "video/webm" : "video/mp4";
        String label = session.getTopic() != null && !session.getTopic().isBlank()
                ? session.getTopic() : session.getCourseTitle() != null ? session.getCourseTitle() : "class";
        String safe = label.replaceAll("[^A-Za-z0-9 _-]", "").trim().replaceAll("\\s+", "-");
        if (safe.isEmpty()) {
            safe = "class";
        }
        return new RecordingFile(file, type, safe + "-" + session.getSessionDate() + extension);
    }

    /**
     * Deletes the finished recording from the server, for the class's trainer or staff - typically right after they
     * have downloaded it, to free the disk. A recording still being made cannot be deleted.
     */
    @Transactional
    public void delete(Long liveSessionId) {
        LiveSession session = lockSession(liveSessionId);
        hostAccess.requireHostOf(session);
        if (session.isBeingRecorded()) {
            throw new BusinessRuleException("RECORDING_IN_PROGRESS", "Stop the recording before deleting it.");
        }
        if (session.getRecordingUrl() == null && session.getRecordingFilePath() == null) {
            throw new ResourceNotFoundException("This class has no recording");
        }
        long bytes = session.getRecordingBytes();
        String path = session.getRecordingFilePath();
        if (path != null) {
            try {
                Path file = Path.of(path);
                Files.deleteIfExists(file);
                Path dir = file.getParent();
                if (dir != null && Files.isDirectory(dir)) {
                    try (var rest = Files.list(dir)) {
                        if (rest.findAny().isEmpty()) {
                            Files.deleteIfExists(dir);
                        }
                    }
                }
            } catch (IOException ex) {
                throw new UncheckedIOException("Could not delete the recording file", ex);
            }
        }
        session.clearRecording();
        sessions.save(session);
        events.audit(SERVICE_NAME, "LIVE_RECORDING_DELETED", "LiveSession", liveSessionId, null,
                Map.of("bytes", bytes));
    }

    private LiveSession lockSession(Long id) {
        return sessions.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Live session", id));
    }

    private String filepathFor(LiveSession session, String extension) {
        Path dir = Path.of(properties.getRecordingStoragePath(), session.getRoomName());
        return dir.resolve(Instant.now().toEpochMilli() + extension).toString().replace('\\', '/');
    }

    private LiveSession requireSession(Long id) {
        return sessions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Live session", id));
    }
}
