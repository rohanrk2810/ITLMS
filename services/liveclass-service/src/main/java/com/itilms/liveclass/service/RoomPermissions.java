package com.itilms.liveclass.service;

import java.util.ArrayList;
import java.util.List;

import com.itilms.liveclass.entity.LiveParticipant;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.ParticipantRole;

/**
 * What one person may switch on in a live room: a microphone, a camera, a screen share.
 *
 * <p>The role decides first. A trainer or a staff member (administrator, coordinator) may always use all three: the
 * controls that switch them off for others are theirs. A student follows the room's policy unless a host has
 * overridden it for them personally. This is a security rule, not a label: the join token carries exactly these
 * sources, LiveKit refuses anything else, and a change made during the class is pushed to LiveKit at once.
 */
public final class RoomPermissions {

    public static final String MICROPHONE = "microphone";
    public static final String CAMERA = "camera";
    public static final String SCREEN_SHARE = "screen_share";
    public static final String SCREEN_SHARE_AUDIO = "screen_share_audio";

    private RoomPermissions() {
    }

    /** The answer for one person. */
    public record Effective(boolean microphone, boolean camera, boolean screenShare) {

        /** The LiveKit track sources this person may publish. Screen-share sound goes with the screen. */
        public List<String> sources() {
            List<String> sources = new ArrayList<>(4);
            if (microphone) {
                sources.add(MICROPHONE);
            }
            if (camera) {
                sources.add(CAMERA);
            }
            if (screenShare) {
                sources.add(SCREEN_SHARE);
                sources.add(SCREEN_SHARE_AUDIO);
            }
            return sources;
        }

        public boolean canPublishAnything() {
            return microphone || camera || screenShare;
        }
    }

    /** {@code participant} may be null for someone who has not been registered in the room yet. */
    public static Effective of(ParticipantRole role, LiveSession session, LiveParticipant participant) {
        if (role != ParticipantRole.STUDENT) {
            return new Effective(true, true, true);
        }
        return new Effective(
                choose(participant == null ? null : participant.getMicAllowed(), session.isStudentsCanMic()),
                choose(participant == null ? null : participant.getCameraAllowed(), session.isStudentsCanCamera()),
                choose(participant == null ? null : participant.getScreenAllowed(), session.isStudentsCanShareScreen()));
    }

    private static boolean choose(Boolean override, boolean roomPolicy) {
        return override != null ? override : roomPolicy;
    }
}
