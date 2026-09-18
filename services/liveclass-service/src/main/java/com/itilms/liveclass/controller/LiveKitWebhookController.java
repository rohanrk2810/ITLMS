package com.itilms.liveclass.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.liveclass.config.LiveKitProperties;
import com.itilms.liveclass.livekit.WebhookNotice;
import com.itilms.liveclass.service.LiveKitWebhookHandler;

import io.livekit.server.WebhookReceiver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import livekit.LivekitWebhook;
import lombok.extern.slf4j.Slf4j;

/**
 * Receives room and participant notices from the LiveKit server.
 *
 * <p>This path is outside the JWT filter chain because LiveKit has no IT-ILMS
 * account. It is not, however, open: LiveKit signs every delivery with a JWT
 * carrying a SHA-256 of the body, made with the shared API secret. The receiver
 * checks that signature and that hash before anything is read. A forged request
 * - someone posting "student 42 was in the room for three hours" - fails here
 * and never reaches the attendance records.
 *
 * <p>The body is taken as the raw string rather than bound to an object, because
 * the hash is over the exact bytes sent. Parsing and re-serialising first would
 * change whitespace and break verification for every genuine delivery.
 */
@Slf4j
@Tag(name = "Live class webhook", description = "Called by the LiveKit server, not by people")
@RestController
@RequestMapping("/api/liveclass/webhook")
public class LiveKitWebhookController {

    private final WebhookReceiver receiver;
    private final LiveKitWebhookHandler handler;

    public LiveKitWebhookController(LiveKitProperties properties, LiveKitWebhookHandler handler) {
        this.receiver = new WebhookReceiver(properties.webhookKeyOrApiKey(), properties.getApiSecret());
        this.handler = handler;
    }

    @Operation(summary = "LiveKit event delivery",
            description = "Signed by LiveKit. Unsigned or tampered deliveries are rejected with 401.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Accepted (including duplicates and unknown rooms)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid LiveKit signature")
    })
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody String body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        if (authorization == null || authorization.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        LivekitWebhook.WebhookEvent event;
        try {
            event = receiver.receive(body, authorization);
        } catch (Exception ex) {
            log.warn("Rejected LiveKit webhook with an invalid signature: {}", ex.getMessage());
            return ResponseEntity.status(401).build();
        }

        // A processing failure propagates as a 5xx on purpose: LiveKit retries
        // until it gets a 2xx, and the delivery id makes the retry safe.
        handler.handle(WebhookNotice.from(event));
        return ResponseEntity.ok().build();
    }
}
