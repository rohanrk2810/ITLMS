 package com.itilms.liveclass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.liveclass.dto.request.MuteRequest;
import com.itilms.liveclass.dto.request.ParticipantPermissionRequest;
import com.itilms.liveclass.dto.request.RoomPolicyRequest;
import com.itilms.liveclass.entity.LiveParticipant;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.LiveSessionStatus;
import com.itilms.liveclass.entity.ParticipantRole;
import com.itilms.liveclass.livekit.LiveKitGateway;
import com.itilms.liveclass.repository.LiveParticipantRepository;
import com.itilms.liveclass.repository.LiveSessionRepository;

class RoomControlServiceTest {

    private LiveSessionRepository sessions;
    private LiveParticipantRepository participants;
    private HostAccess hostAccess;
    private LiveKitGateway liveKit;
    private RoomControlService service;

    private LiveSession session;
    private LiveParticipant student;
    private LiveParticipant absentStudent;
    private LiveParticipant trainer;

    @BeforeEach
    void setUp() {
        sessions = mock(LiveSessionRepository.class);
        participants = mock(LiveParticipantRepository.class);
        hostAccess = mock(HostAccess.class);
        liveKit = mock(LiveKitGateway.class);
        service = new RoomControlService(sessions, participants, hostAccess, liveKit, mock(EventPublisher.class));

        session = new LiveSession();
        session.setId(1L);
        session.setRoomName("room-1");
        session.setStatus(LiveSessionStatus.LIVE);
        student = person(10L, ParticipantRole.STUDENT, Instant.now());
        absentStudent = person(11L, ParticipantRole.STUDENT, null);
        trainer = person(20L, ParticipantRole.TRAINER, Instant.now());

        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        when(participants.findByLiveSessionIdOrderByDisplayNameAsc(1L)).thenReturn(List.of(student, absentStudent, trainer));
        when(participants.findByLiveSessionIdAndUserId(1L, 10L)).thenReturn(Optional.of(student));
        when(participants.findByLiveSessionIdAndUserId(1L, 20L)).thenReturn(Optional.of(trainer));
    }

    private static LiveParticipant person(Long userId, ParticipantRole role, Instant inRoomSince) {
        return LiveParticipant.builder().liveSessionId(1L).userId(userId).identity("user-" + userId)
                .displayName("P" + userId).role(role).currentJoinAt(inRoomSince).build();
    }

    @Test
    void policyIsSavedAndPushedOnlyToStudentsWhoAreInTheRoom() {
        service.updatePolicy(1L, new RoomPolicyRequest(false, null, true));

        assertThat(session.isStudentsCanMic()).isFalse();
        assertThat(session.isStudentsCanCamera()).isTrue();
        assertThat(session.isStudentsCanShareScreen()).isTrue();
        verify(sessions).save(session);
        verify(liveKit).updatePublishPermissions(eq("room-1"), eq("user-10"), anyList());
        verify(liveKit, never()).updatePublishPermissions(eq("room-1"), eq("user-11"), anyList());
        verify(liveKit, never()).updatePublishPermissions(eq("room-1"), eq("user-20"), anyList());
    }

    @Test
    void anOverrideIsStoredAndFollowRoomClearsIt() {
        service.updateParticipant(1L, 10L, new ParticipantPermissionRequest(true, false, null, null));
        assertThat(student.getMicAllowed()).isTrue();
        assertThat(student.getCameraAllowed()).isFalse();
        assertThat(student.getScreenAllowed()).isNull();

        service.updateParticipant(1L, 10L, new ParticipantPermissionRequest(null, null, null, true));
        assertThat(student.getMicAllowed()).isNull();
        assertThat(student.getCameraAllowed()).isNull();
    }

    @Test
    void hostsCannotBeRestricted() {
        assertThatThrownBy(() -> service.updateParticipant(1L, 20L, new ParticipantPermissionRequest(false, false, false, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.mute(1L, 20L, new MuteRequest(MuteRequest.Source.MICROPHONE)))
                .isInstanceOf(BusinessRuleException.class);
        verify(liveKit, never()).muteSources(anyString(), anyString(), anyList());
    }

    @Test
    void aFinishedClassCannotBeControlled() {
        session.setStatus(LiveSessionStatus.ENDED);
        assertThatThrownBy(() -> service.updatePolicy(1L, new RoomPolicyRequest(false, false, false)))
                .isInstanceOf(BusinessRuleException.class);
        verify(sessions, never()).save(any());
    }

    @Test
    void someoneWhoIsNotAHostIsRefusedBeforeAnythingChanges() {
        doThrow(new ForbiddenOperationException("no")).when(hostAccess).requireHostOf(session);
        assertThatThrownBy(() -> service.updatePolicy(1L, new RoomPolicyRequest(false, false, false)))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.muteAll(1L)).isInstanceOf(ForbiddenOperationException.class);
        verify(sessions, never()).save(any());
        verify(liveKit, never()).muteMicrophonesExcept(anyString(), any());
    }

    @Test
    void muteAllLeavesTheHostsAlone() {
        when(liveKit.muteMicrophonesExcept(eq("room-1"), any())).thenReturn(3);
        assertThat(service.muteAll(1L)).isEqualTo(3);
        verify(liveKit).muteMicrophonesExcept("room-1", java.util.Set.of("user-20"));
    }

    @Test
    void muteScreenShareAlsoMutesItsSound() {
        service.mute(1L, 10L, new MuteRequest(MuteRequest.Source.SCREEN_SHARE));
        verify(liveKit).muteSources("room-1", "user-10", List.of("screen_share", "screen_share_audio"));
    }
}
