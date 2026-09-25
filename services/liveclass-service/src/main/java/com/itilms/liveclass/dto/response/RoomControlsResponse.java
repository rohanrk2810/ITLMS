package com.itilms.liveclass.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** The room's policy and, for each person who has joined, what they may switch on right now. */
@Schema(description = "Who may use a microphone, camera or screen share in a live class")
public record RoomControlsResponse(Policy policy, List<ParticipantControl> participants) {

    @Schema(description = "What students may switch on unless a host overrides it for one of them")
    public record Policy(boolean studentsCanMic, boolean studentsCanCamera, boolean studentsCanShareScreen) {
    }

    public record ParticipantControl(
            Long userId,
            String displayName,
            String role,
            boolean inRoom,
            @Schema(description = "What this person may switch on now, whatever the reason")
            boolean microphone,
            boolean camera,
            boolean screenShare,
            @Schema(description = "A host's override for this student; null means follow the room policy")
            Boolean microphoneOverride,
            Boolean cameraOverride,
            Boolean screenShareOverride) {
    }
}