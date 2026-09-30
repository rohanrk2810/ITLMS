package com.itilms.liveclass.service;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;

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
        if (session.getEgressId() != null) {
            throw new BusinessRuleException("ALREADY_RECORDING", "This class is already being recorded.");
        }

        String filepath = filepathFor(session);
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

    private String filepathFor(LiveSession session) {
        Path dir = Path.of(properties.getRecordingStoragePath(), session.getRoomName());
        return dir.resolve(Instant.now().toEpochMilli() + ".mp4").toString().replace('\\', '/');
    }

    private LiveSession requireSession(Long id) {
        return sessions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Live session", id));
    }
}
