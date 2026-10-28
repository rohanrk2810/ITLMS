package com.itilms.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.itilms.common.exception.ApiException;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.identity.config.IdentityProperties;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Checks, on the server, that the person at the form passed the "verify you are human" widget.
 *
 * <p>The widget alone proves nothing: a script can skip it and call the API directly. So every protected endpoint
 * sends the widget's token here, and the token is checked with Cloudflare using the secret key. A token works once.
 *
 * <p>If Cloudflare cannot be reached the request is refused, not waved through: a captcha that opens when it
 * cannot check is no captcha. With the captcha switched off ({@code itilms.captcha.enabled=false}) nothing is checked.
 */
@Slf4j
@Service
public class CaptchaVerifier {

    private final IdentityProperties.Captcha settings;
    private final RestClient http;

    @Autowired
    public CaptchaVerifier(IdentityProperties properties, RestClient.Builder builder) {
        this(properties, withTimeouts(builder, properties.getCaptcha()));
    }

    /** For tests, which supply a client wired to a fake server. */
    CaptchaVerifier(IdentityProperties properties, RestClient http) {
        this.settings = properties.getCaptcha();
        this.http = http;
    }

    private static RestClient withTimeouts(RestClient.Builder builder, IdentityProperties.Captcha settings) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout((int) settings.getTimeout().toMillis());
        timeouts.setReadTimeout((int) settings.getTimeout().toMillis());
        return builder.requestFactory(timeouts).build();
    }

    /** The header the desktop app sends its key in. */
    public static final String DESKTOP_KEY_HEADER = "X-Desktop-Key";
    private static final int MIN_DESKTOP_KEY_LENGTH = 24;

    @PostConstruct
    void refuseToRunHalfConfigured() {
        if (!isBlank(settings.getDesktopKey()) && settings.getDesktopKey().length() < MIN_DESKTOP_KEY_LENGTH) {
            throw new IllegalStateException(
                    "CAPTCHA_DESKTOP_KEY is set but shorter than " + MIN_DESKTOP_KEY_LENGTH + " characters");
        }
        if (settings.isEnabled() && (isBlank(settings.getSecret()) || isBlank(settings.getSiteKey()))) {
            throw new IllegalStateException(
                    "The captcha is switched on (CAPTCHA_ENABLED=true) but CAPTCHA_SITE_KEY or CAPTCHA_SECRET is empty");
        }
    }

    /** Whether the request carries the desktop app's key. Always false when no key is configured. */
    public boolean isDesktop(String presentedKey) {
        String key = settings.getDesktopKey();
        if (isBlank(key) || isBlank(presentedKey)) {
            return false;
        }
        return MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), presentedKey.getBytes(StandardCharsets.UTF_8));
    }

    /** {@link #config()}, except that the desktop app is told there is no widget to show. */
    public Config config(String desktopKey) {
        return isDesktop(desktopKey) ? new Config(false, "") : config();
    }

    /** {@link #require(String, String)}, except that the desktop app's key stands in for the widget. */
    public void require(String token, String clientIp, String desktopKey) {
        if (isDesktop(desktopKey)) {
            return;
        }
        require(token, clientIp);
    }

    public boolean enabled() {
        return settings.isEnabled();
    }

    /** What the browser needs to know: whether to show the widget, and with which public key. */
    public Config config() {
        return new Config(settings.isEnabled(), settings.isEnabled() ? settings.getSiteKey() : "");
    }

    /**
     * Throws unless the token is genuine. Does nothing when the captcha is off.
     *
     * @param token    what the widget gave the browser
     * @param clientIp the visitor's address, passed along so Cloudflare can judge the token better; may be null
     */
    public void require(String token, String clientIp) {
        if (!settings.isEnabled()) {
            return;
        }
        if (isBlank(token)) {
            throw new BusinessRuleException("CAPTCHA_REQUIRED", "Please confirm you are not a robot.");
        }

        var form = new LinkedMultiValueMap<String, String>();
        form.add("secret", settings.getSecret());
        form.add("response", token);
        if (!isBlank(clientIp)) {
            form.add("remoteip", clientIp);
        }

        Result result;
        try {
            result = http.post()
                    .uri(settings.getVerifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Result.class);
        } catch (RestClientException ex) {
            log.warn("Could not reach the captcha service: {}", ex.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAPTCHA_UNAVAILABLE",
                    "The check could not be completed. Please try again in a moment.");
        }

        if (result == null || !result.success()) {
            log.info("Captcha refused: {}", result == null ? "no answer" : result.errorCodes());
            throw new BusinessRuleException("CAPTCHA_FAILED", "The check failed. Please try again.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record Config(boolean enabled, String siteKey) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(boolean success, @JsonProperty("error-codes") List<String> errorCodes) {
    }
}
