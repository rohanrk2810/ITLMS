package com.itilms.liveclass.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.itilms.liveclass.config.LiveClassProperties;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.livekit.LiveKitGateway;
import com.itilms.liveclass.livekit.LiveRoomProvisioner;
import com.itilms.liveclass.repository.LiveSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The two jobs webhooks cannot be relied on to do.
 *
 * <p><b>Before class:</b> creates LiveKit rooms for sessions starting soon, so
 * the first join does not wait on room creation.
 *
 * <p><b>After class:</b> settles sessions whose end has long passed but whose
 * attendance was never published - because the final webhook was lost, because
 * LiveKit restarted mid-class, or because nobody ever opened the room. Without
 * this a register can stay blank indefinitely, and a blank register reads as
 * "not yet marked", not as the problem it is.
 *
 * <p>Both halves are safe to run on several instances at once: room creation is
 * idempotent on LiveKit's side, and settlement locks the row and checks it has
 * not already been done.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveSessionSweepJob {

    private final LiveSessionRepository sessionRepository;
    private final LiveAttendanceService attendanceService;
    private final LiveKitGateway liveKit;
    private final LiveRoomProvisioner roomProvisioner;
    private final LiveClassProperties props;

    @Scheduled(fixedDelayString = "${itilms.live-class.sweep-interval-ms:60000}",
            initialDelayString = "${itilms.live-class.sweep-initial-delay-ms:30000}")
    public void sweep() {
        preProvisionRooms();
        settleFinishedSessions();
    }

    void preProvisionRooms() {
        Instant now = Instant.now();
        var due = sessionRepository.findDueForProvisioning(
                now, now.plus(Duration.ofMinutes(props.getPreProvisionMinutes())));

        for (LiveSession session : due) {
            try {
                roomProvisioner.ensureRoom(session);
                log.info("Pre-created live room {} for {}", session.getRoomName(), session.getScheduledStartAt());
            } catch (Exception ex) {
                // One room failing - LiveKit briefly down - must not stop the
                // rest. The next run tries again, and a join creates it anyway.
                log.warn("Could not pre-create live room {}: {}", session.getRoomName(), ex.getMessage());
            }
        }
    }

    void settleFinishedSessions() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(props.getSettleAfterEndMinutes()));

        for (LiveSession session : sessionRepository.findUnsettledBefore(cutoff)) {
            try {
                // No close time is passed: settlement works it out from the
                // room's own activity, so a class that emptied at 10:40 is not
                // treated as having run to 11:00.
                if (attendanceService.settle(session.getId(), null)) {
                    liveKit.deleteRoomQuietly(session.getRoomName());
                    log.info("Sweep settled live session {} (class session {})",
                            session.getId(), session.getClassSessionId());
                }
            } catch (Exception ex) {
                log.error("Sweep could not settle live session {}", session.getId(), ex);
            }
        }
    }
}
