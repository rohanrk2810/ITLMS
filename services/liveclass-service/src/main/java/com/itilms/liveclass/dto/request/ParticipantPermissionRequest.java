package com.itilms.liveclass.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** One student's permissions, overriding the room policy. A field left out is left as it is. */
@Schema(description = "Permissions for one student")
public record ParticipantPermissionRequest(
        Boolean microphone,
        Boolean camera,
        Boolean screenShare,
        @Schema(description = "True clears every override, so this student follows the room policy again")
        Boolean followRoom
) {
}