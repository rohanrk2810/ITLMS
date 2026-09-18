package com.itilms.common.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * Verifies access tokens. Every business service holds one of these; only
 * identity-service can mint tokens.
 *
 * <p>Deliberately returns {@code null} rather than throwing on a bad token: an
 * unreadable token is an anonymous request, and the security chain turns that
 * into 401 in one place.
 */
@Slf4j
@Component
public class JwtVerifier {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_PROFILE_ID = "pid";
    public static final String CLAIM_TOKEN_TYPE = "typ";

    public static final String TYPE_ACCESS = "ACCESS";
    public static final String TYPE_REFRESH = "REFRESH";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtVerifier(JwtProperties properties) {
        this.properties = properties;
        this.key = buildKey(properties.getSecret());
    }

    /**
     * Accepts either a base64-encoded secret or a raw passphrase, so operators
     * can paste whichever their secret manager produces.
     */
    static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "itilms.jwt.secret is not configured. Set the JWT_SECRET environment variable.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException notBase64) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "itilms.jwt.secret must decode to at least 32 bytes for HS256; got " + bytes.length);
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    /** @return the principal carried by a valid ACCESS token, or {@code null}. */
    public AppPrincipal verifyAccessToken(String token) {
        Claims claims = parse(token);
        if (claims == null) {
            return null;
        }
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            log.debug("Rejected a non-access token presented as a bearer credential");
            return null;
        }
        Number profileId = claims.get(CLAIM_PROFILE_ID, Number.class);
        return new AppPrincipal(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NAME, String.class),
                claims.get(CLAIM_ROLE, String.class),
                profileId == null ? null : profileId.longValue());
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .clockSkewSeconds(properties.getClockSkew().toSeconds())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return null;
        }
    }

    protected SecretKey key() {
        return key;
    }
}
