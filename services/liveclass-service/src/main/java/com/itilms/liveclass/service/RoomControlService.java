package com.itilms.liveclass.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.liveclass.dto.request.MuteRequest;
import com.itilms.liveclass.dto.request.ParticipantPermissionRequest;
import com.itilms.liveclass.dto.request.RoomPolicyRequest;
import com.itilms.liveclass.dto.response.RoomControlsResponse;
import com.itilms.liveclass.dto.response.RoomControlsResponse.ParticipantControl;
import com.itilms.liveclass.entity.LiveParticipant;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.ParticipantRole;
import com.itilms.liveclass.livekit.LiveKitGateway;
import com.itilms.liveclass.repository.LiveParticipantRepository;
import com.itilms.liveclass.repository.LiveSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The host's controls over a running class: what students may switch on, per room and per person, and muting.
 *
 * <p>Only the class trainer and staff (administrators and coordinators) may use them, checked here. Students have
 * no route to any of it: what a student may do is written into their join token from the answer computed by
 * {@link RoomPermissions}, and every change is pushed to LiveKit at once so it takes effect mid-class.
 *
 * <p>Like the rest of live-class handling this is deliberately not transactional: each write is one short
 * statement, and the LiveKit calls after it must not hold a database connection open.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomControlService {

    private static final String SERVICE_NAME = "liveclass-service";
    private static final List<String> ALL_SOURCES = List.of(RoomPermissions.MICROPHONE, RoomPermissions.CAMERA,
            RoomPermissions.SCREEN_SHARE, RoomPermissions.SCREEN_SHARE_AUDIO);

    private final LiveSessionRepository sessions;
    private final LiveParticipantRepository participants;
    private final HostAccess hostAccess;
    private final LiveKitGateway liveKit;
    private final EventPublisher events;

    public RoomControlsResponse controls(Long liveSessionId) {
        LiveSession session = requireSession(liveSessionId);
        hostAccess.requireHostOf(session);
        return controlsOf(session);
    }

    /**
     * Sets what students may switch on, and applies it to the students in the room right now. A student's own
     * override (if a host gave them one) still wins over the room policy.
     */
    public RoomControlsResponse updatePolicy(Long liveSessionId, RoomPolicyRequest request) {
        LiveSession session = requireRunnable(liveSessionId);
        if (request.studentsCanMic() != null) {
            session.setStudentsCanMic(request.studentsCanMic());
        }
        if (request.studentsCanCamera() != null) {
            session.setStudentsCanCamera(request.studentsCanCamera());
        }
        if (request.studentsCanShareScreen() != null) {
            session.setStudentsCanShareScreen(request.studentsCanShareScreen());
        }
        sessions.save(session);

        int failed = 0;
        for (LiveParticipant participant : participants.findByLiveSessionIdOrderByDisplayNameAsc(liveSessionId)) {
            if (participant.getRole() == ParticipantRole.STUDENT && participant.inRoom()) {
                try {
                    push(session, participant);
                } catch (RuntimeException ex) {
                    failed++;
                    log.warn("Could not update {} in room {}: {}", participant.getIdentity(), session.getRoomName(), ex.getMessage());
                }
            }
        }
        events.audit(SERVICE_NAME, "LIVE_ROOM_POLICY_CHANGED", "LiveSession", liveSessionId, null,
                Map.of("mic", session.isStudentsCanMic(), "camera", session.isStudentsCanCamera(),
                        "screenShare", session.isStudentsCanShareScreen(), "notApplied", failed));
        return controlsOf(session);
    }

    /** Overrides the policy for one student: allow or deny each of the three, or clear the overrides. */
    public RoomControlsResponse updateParticipant(Long liveSessionId, Long userId, ParticipantPermissionRequest request) {
        LiveSession session = requireRunnable(liveSessionId);
        LiveParticipant participant = requireStudent(liveSessionId, userId);

        if (Boolean.TRUE.equals(request.followRoom())) {
            participant.setMicAllowed(null);
            participant.setCameraAllowed(null);
            participant.setScreenAllowed(null);
        } else {
            if (request.microphone() != null) {
                participant.setMicAllowed(request.microphone());
            }
            if (request.camera() != null) {
                participant.setCameraAllowed(request.camera());
            }
            if (request.screenShare() != null) {
                participant.setScreenAllowed(request.screenShare());
            }
        }
        participants.save(participant);

        // The override is saved either way, so it holds if they rejoin; a failure to push it to a person in the
        // room is reported, because the host would otherwise believe something that is not yet true.
        if (participant.inRoom()) {
            push(session, participant);
        }
        events.audit(SERVICE_NAME, "LIVE_PARTICIPANT_PERMISSIONS_CHANGED", "LiveSession", liveSessionId, null,
                Map.of("userId", userId));
        return controlsOf(session);
    }

    /** Switches off one media source of one student. They may switch it on again if they are still permitted it. */
    public int mute(Long liveSessionId, Long userId, MuteRequest request) {
        LiveSession session = requireRunnable(liveSessionId);
        LiveParticipant participant = requireStudent(liveSessionId, userId);
        String source = switch (request.source()) {
            case MICROPHONE -> RoomPermissions.MICROPHONE;
            case CAMERA -> RoomPermissions.CAMERA;
            case SCREEN_SHARE -> RoomPermissions.SCREEN_SHARE;
        };
        List<String> sources = request.source() == MuteRequest.Source.SCREEN_SHARE
                ? List.of(RoomPermissions.SCREEN_SHARE, RoomPermissions.SCREEN_SHARE_AUDIO) : List.of(source);
        int muted = liveKit.muteSources(session.getRoomName(), participant.getIdentity(), sources);
        events.audit(SERVICE_NAME, "LIVE_PARTICIPANT_MUTED", "LiveSession", liveSessionId, null,
                Map.of("userId", userId, "source", request.source().name()));
        return muted;
    }

    /** Mutes every microphone in the room except the hosts'. Students who are still permitted may unmute. */
    public int muteAll(Long liveSessionId) {
        LiveSession session = requireRunnable(liveSessionId);
        Set<String> hosts = participants.findByLiveSessionIdOrderByDisplayNameAsc(liveSessionId).stream()
                .filter(p -> p.getRole() != ParticipantRole.STUDENT)
                .map(LiveParticipant::getIdentity).collect(Collectors.toSet());
        int muted = liveKit.muteMicrophonesExcept(session.getRoomName(), hosts);
        events.audit(SERVICE_NAME, "LIVE_ROOM_MUTED_ALL", "LiveSession", liveSessionId, null, Map.of("muted", muted));
        return muted;
    }

    // -----------------------------------------------------------------

    /**
     * Tells LiveKit what this person may now publish, and switches off anything they are publishing that they no
     * longer may - permission changes do not always take a live track off air by themselves.
     */
    private void push(LiveSession session, LiveParticipant participant) {
        RoomPermissions.Effective now = RoomPermissions.of(participant.getRole(), session, participant);
        List<String> allowed = now.sources();
        liveKit.updatePublishPermissions(session.getRoomName(), participant.getIdentity(), allowed);
        List<String> revoked = ALL_SOURCES.stream().filter(s -> !allowed.contains(s)).toList();
        if (!revoked.isEmpty()) {
            liveKit.muteSources(session.getRoomName(), participant.getIdentity(), revoked);
        }
    }

    private RoomControlsResponse controlsOf(LiveSession session) {
        List<ParticipantControl> people = participants.findByLiveSessionIdOrderByDisplayNameAsc(session.getId()).stream()
                .map(p -> {
                    RoomPermissions.Effective e = RoomPermissions.of(p.getRole(), session, p);
                    return new ParticipantControl(p.getUserId(), p.getDisplayName(), p.getRole().name(), p.inRoom(),
                            e.microphone(), e.camera(), e.screenShare(),
                            p.getMicAllowed(), p.getCameraAllowed(), p.getScreenAllowed());
                })
                .toList();
        return new RoomControlsResponse(new RoomControlsResponse.Policy(session.isStudentsCanMic(),
                session.isStudentsCanCamera(), session.isStudentsCanShareScreen()), people);
    }

    /** A host of this class, on a class that has not finished: nothing can be controlled in a room that is gone. */
    private LiveSession requireRunnable(Long liveSessionId) {
        LiveSession session = requireSession(liveSessionId);
        hostAccess.requireHostOf(session);
        if (session.getStatus().isSettled() || session.isAttendanceComputed()) {
            throw new BusinessRuleException("CLASS_ENDED", "This class has finished, so its room can no longer be controlled.");
        }
        return session;
    }

    private LiveParticipant requireStudent(Long liveSessionId, Long userId) {
        LiveParticipant participant = participants.findByLiveSessionIdAndUserId(liveSessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("That person has not joined this class"));
        if (participant.getRole() != ParticipantRole.STUDENT) {
            throw new BusinessRuleException("CANNOT_RESTRICT_HOST", "Only students' media can be controlled.");
        }
        return participant;
    }

    private LiveSession requireSession(Long id) {
        return sessions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Live session", id));
    }
}
