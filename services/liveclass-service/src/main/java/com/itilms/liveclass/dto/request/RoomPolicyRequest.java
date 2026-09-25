package com.itilms.liveclass.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** What students may switch on in the room. A field left out is left as it is. */
@Schema(description = "Room policy for students")
public record RoomPolicyRequest(
        Boolean studentsCanMic,
        Boolean studentsCanCamera,
        @Schema(description = "Screen sharing is off for students unless a host turns it on")
        Boolean studentsCanShareScreen
) {
}