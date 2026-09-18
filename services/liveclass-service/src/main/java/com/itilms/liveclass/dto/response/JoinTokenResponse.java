package com.itilms.liveclass.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything the browser needs to enter a live class, and nothing more.
 *
 * <p>The token is short-lived and carries its own grants; the client cannot
 * widen them. What the client <em>can</em> usefully know is what those grants
 * were, which is why {@code canPublish} and {@code roomAdmin} are echoed back -
 * so the UI shows a camera button to someone who has one, rather than offering
 * a control that the media server will silently refuse.
 */
@Schema(description = "Credentials for joining a live class room")
public record JoinTokenResponse(

        Long liveSessionId,
        Long classSessionId,
        Long batchId,
        String batchCode,
        String courseTitle,
        String topic,

        @Schema(description = "LiveKit room to connect to")
        String roomName,

        @Schema(description = "WebSocket URL of the media server, as reachable from the browser",
                example = "wss://live.example.edu")
        String serverUrl,

        @Schema(description = "Signed LiveKit access token")
        String token,

        @Schema(description = "This participant's LiveKit identity", example = "user-42")
        String identity,
        String displayName,

        @Schema(description = "TRAINER, STUDENT or STAFF")
        String role,

        @Schema(description = "May this participant turn on a camera or microphone")
        boolean canPublish,

        @Schema(description = "May this participant mute or remove others")
        boolean roomAdmin,

        @Schema(description = "Whether the room is being recorded")
        boolean recording,

        Instant scheduledStartAt,
        Instant scheduledEndAt,

        @Schema(description = "When the token stops working")
        Instant expiresAt
) {
}
