package com.itilms.liveclass.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.itilms.liveclass.entity.LiveParticipant;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.ParticipantRole;

class RoomPermissionsTest {

    @Test
    void studentsFollowTheDefaultPolicyMicAndCameraOnScreenOff() {
        var e = RoomPermissions.of(ParticipantRole.STUDENT, new LiveSession(), null);
        assertThat(e.microphone()).isTrue();
        assertThat(e.camera()).isTrue();
        assertThat(e.screenShare()).isFalse();
        assertThat(e.sources()).containsExactly("microphone", "camera");
    }

    @Test
    void anOverrideBeatsTheRoomPolicyInBothDirections() {
        LiveSession session = new LiveSession();
        session.setStudentsCanMic(false);
        LiveParticipant p = LiveParticipant.builder().micAllowed(true).screenAllowed(true).cameraAllowed(false).build();

        var e = RoomPermissions.of(ParticipantRole.STUDENT, session, p);

        assertThat(e.microphone()).isTrue();
        assertThat(e.camera()).isFalse();
        assertThat(e.screenShare()).isTrue();
        assertThat(e.sources()).contains("screen_share", "screen_share_audio");
    }

    @Test
    void hostsCanAlwaysUseEverythingWhateverThePolicySays() {
        LiveSession session = new LiveSession();
        session.setStudentsCanMic(false);
        session.setStudentsCanCamera(false);
        LiveParticipant p = LiveParticipant.builder().micAllowed(false).build();

        for (ParticipantRole role : new ParticipantRole[]{ParticipantRole.TRAINER, ParticipantRole.STAFF}) {
            var e = RoomPermissions.of(role, session, p);
            assertThat(e.microphone() && e.camera() && e.screenShare()).isTrue();
        }
    }

    @Test
    void aStudentWithNothingAllowedIsAnObserver() {
        LiveSession session = new LiveSession();
        session.setStudentsCanMic(false);
        session.setStudentsCanCamera(false);
        var e = RoomPermissions.of(ParticipantRole.STUDENT, session, null);
        assertThat(e.canPublishAnything()).isFalse();
        assertThat(e.sources()).isEmpty();
    }
}
