package com.itilms.identity.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.itilms.common.exception.ApiException;
import com.itilms.identity.config.IdentityProperties;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends a sign-in code through the Meta WhatsApp Cloud API, using an approved authentication template
 * (one body variable, the code, and the one-tap copy-code button, which takes the same code).
 */
@Slf4j
@Service
public class WhatsAppSender {

    private final IdentityProperties.WhatsApp settings;
    private final RestClient http;

    @Autowired
    public WhatsAppSender(IdentityProperties properties, RestClient.Builder builder) {
        this(properties, withTimeouts(builder, properties.getWhatsapp()));
    }

    /** For tests, which supply a client wired to a fake server. */
    WhatsAppSender(IdentityProperties properties, RestClient http) {
        this.settings = properties.getWhatsapp();
        this.http = http;
    }

    private static RestClient withTimeouts(RestClient.Builder builder, IdentityProperties.WhatsApp settings) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout((int) settings.getTimeout().toMillis());
        timeouts.setReadTimeout((int) settings.getTimeout().toMillis());
        return builder.requestFactory(timeouts).build();
    }

    @PostConstruct
    void refuseToRunHalfConfigured() {
        if (settings.isEnabled() && !settings.isDevLogCodes() && (isBlank(settings.getPhoneNumberId())
                || isBlank(settings.getAccessToken()) || isBlank(settings.getTemplateName()))) {
            throw new IllegalStateException("WhatsApp sign-in is switched on (WHATSAPP_ENABLED=true) but "
                    + "WHATSAPP_PHONE_NUMBER_ID, WHATSAPP_ACCESS_TOKEN or WHATSAPP_TEMPLATE_NAME is empty");
        }
    }

    public boolean enabled() {
        return settings.isEnabled();
    }

    /** @param to country code plus number, digits only */
    public void sendCode(String to, String code) {
        if (settings.isDevLogCodes()) {
            // Development only; never switch this on where real numbers are used.
            log.warn("WhatsApp dev mode: sign-in code for {} is {}", to, code);
            return;
        }

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "template",
                "template", Map.of(
                        "name", settings.getTemplateName(),
                        "language", Map.of("code", settings.getLanguageCode()),
                        "components", List.of(
                                Map.of("type", "body", "parameters",
                                        List.of(Map.of("type", "text", "text", code))),
                                Map.of("type", "button", "sub_type", "url", "index", "0", "parameters",
                                        List.of(Map.of("type", "text", "text", code))))));
        try {
            http.post()
                    .uri(settings.getApiBase() + "/" + settings.getPhoneNumberId() + "/messages")
                    .header("Authorization", "Bearer " + settings.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            // The message can carry the recipient and the code; log only the cause class.
            log.warn("WhatsApp message could not be sent: {}", ex.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WHATSAPP_UNAVAILABLE",
                    "The code could not be sent on WhatsApp. Please try again in a moment.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
