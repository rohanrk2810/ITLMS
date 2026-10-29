package com.itilms.identity.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.itilms.common.exception.ApiException;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.identity.config.IdentityProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Checks, on the server, a Google ID token the browser got from "Continue with Google".
 *
 * <p>The token is sent to Google's tokeninfo endpoint, which verifies the signature and expiry. The service then
 * insists the token was issued for <em>this</em> client id (otherwise a token minted for some other app would sign in
 * here) and that Google vouches for the email address.
 */
@Slf4j
@Service
public class GoogleTokenVerifier {

    private static final java.util.Set<String> ISSUERS =
            java.util.Set.of("accounts.google.com", "https://accounts.google.com");

    private final IdentityProperties.Google settings;
    private final RestClient http;

    @Autowired
    public GoogleTokenVerifier(IdentityProperties properties, RestClient.Builder builder) {
        this(properties, withTimeouts(builder, properties.getGoogle()));
    }

    /** For tests, which supply a client wired to a fake server. */
    GoogleTokenVerifier(IdentityProperties properties, RestClient http) {
        this.settings = properties.getGoogle();
        this.http = http;
    }

    private static RestClient withTimeouts(RestClient.Builder builder, IdentityProperties.Google settings) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout((int) settings.getTimeout().toMillis());
        timeouts.setReadTimeout((int) settings.getTimeout().toMillis());
        return builder.requestFactory(timeouts).build();
    }

    public boolean enabled() {
        return settings.isEnabled();
    }

    public String clientId() {
        return settings.isEnabled() ? settings.getClientId() : "";
    }

    public Profile verify(String idToken) {
        if (!settings.isEnabled()) {
            throw new BusinessRuleException("GOOGLE_DISABLED", "Sign-in with Google is not switched on.");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new BusinessRuleException("GOOGLE_FAILED", "Google sign-in failed. Please try again.");
        }

        TokenInfo info;
        try {
            info = http.get()
                    .uri(settings.getTokenInfoUrl() + "?id_token={t}", idToken)
                    .retrieve()
                    .body(TokenInfo.class);
        } catch (HttpClientErrorException ex) {
            log.info("Google refused an ID token: {}", ex.getStatusCode());
            throw new BusinessRuleException("GOOGLE_FAILED", "Google sign-in failed. Please try again.");
        } catch (RestClientException ex) {
            log.warn("Could not reach Google: {}", ex.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_UNAVAILABLE",
                    "Google could not be reached. Please try again in a moment.");
        }

        if (info == null
                || !settings.getClientId().equals(info.aud())
                || !ISSUERS.contains(info.iss())
                || !"true".equalsIgnoreCase(info.emailVerified())
                || info.email() == null || info.email().isBlank()) {
            log.info("Google token rejected (audience, issuer or unverified email)");
            throw new BusinessRuleException("GOOGLE_FAILED", "Google sign-in failed. Please try again.");
        }
        return new Profile(info.email().trim().toLowerCase(), info.givenName(), info.familyName(), info.name());
    }

    public record Profile(String email, String givenName, String familyName, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenInfo(
            String aud,
            String iss,
            String email,
            @JsonProperty("email_verified") String emailVerified,
            @JsonProperty("given_name") String givenName,
            @JsonProperty("family_name") String familyName,
            String name) {
    }
}
