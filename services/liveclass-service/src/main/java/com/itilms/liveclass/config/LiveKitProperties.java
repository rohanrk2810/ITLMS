package com.itilms.liveclass.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Connection details for the institute's LiveKit media server.
 *
 * <p>The key and secret are validated as required rather than defaulted. A
 * liveclass-service that started with a blank secret would mint join tokens
 * LiveKit rejects, and the failure would show up as "students cannot enter the
 * class" five minutes before a lecture rather than at boot.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "itilms.livekit")
public class LiveKitProperties {

    /** Server-to-server HTTP base URL, used for room administration. */
    @NotBlank
    private String url = "http://localhost:7880";

    /**
     * The WebSocket URL handed to browsers.
     *
     * <p>Separate from {@link #url} because they genuinely differ in
     * production: the backend reaches LiveKit over the internal network,
     * while the browser reaches it through the public TLS endpoint.
     */
    @NotBlank
    private String wsUrl = "ws://localhost:7880";

    @NotBlank
    private String apiKey;

    @NotBlank
    private String apiSecret;

    /**
     * How long a join token stays usable.
     *
     * <p>Long enough to cover a full class plus overrun, short enough that a
     * token pasted into a group chat stops working the same evening.
     */
    private Duration tokenTtl = Duration.ofHours(4);

    /**
     * The key LiveKit signs its webhooks with. Normally the same as
     * {@link #apiKey}; kept separate so it can be rotated independently.
     */
    private String webhookApiKey;

    /**
     * Where a class recording is written - a directory this container and the {@code livekit-egress} container both
     * mount from the same volume, so a file Egress just finished writing is one this service can read at once.
     */
    private String recordingStoragePath = "/recordings";

    public String webhookKeyOrApiKey() {
        return webhookApiKey == null || webhookApiKey.isBlank() ? apiKey : webhookApiKey;
    }
}
