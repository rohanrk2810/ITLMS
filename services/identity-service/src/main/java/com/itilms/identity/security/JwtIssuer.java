package com.itilms.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.itilms.common.security.JwtProperties;
import com.itilms.common.security.JwtVerifier;
import com.itilms.common.util.Codes;
import com.itilms.identity.entity.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Mints access tokens and refresh tokens. The only place in IT-ILMS that holds
 * a signing key for writing.
 *
 * <p>The two credentials are deliberately different kinds of thing:
 *
 * <ul>
 *   <li><b>Access token — a signed JWT.</b> Self-contained, so eleven other
 *       services can authorize a request without calling back here. The cost is
 *       that it cannot be withdrawn before it expires, which is why it is
 *       short-lived.</li>
 *   <li><b>Refresh token — an opaque random string.</b> There is nothing to
 *       decode: it is a lookup key for a row this service controls. That makes
 *       revocation immediate and real, which a self-contained token can never
 *       offer. It also means a refresh token leaked into a log tells an
 *       attacker nothing about the account it belongs to.</li>
 * </ul>
 *
 * <p>Only the SHA-256 hash of a refresh token is stored. Hashing is enough here
 * — unlike a password, the value is 160 bits of machine-generated randomness,
 * so there is no dictionary to attack and no need for a slow KDF.
 */
@Component
public class JwtIssuer {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtIssuer(JwtProperties properties) {
        this.properties = properties;
        this.key = buildKey(properties.getSecret());
    }

    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "itilms.jwt.secret is not configured; identity-service cannot issue tokens.");
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

    /**
     * Builds the access token.
     *
     * <p>The claims are exactly what a downstream service needs to make an
     * authorization decision without a lookup — id, role, and the student or
     * trainer profile id — and nothing more. Every extra claim is a fact frozen
     * at sign-in time that goes stale until the token expires, so the set is
     * kept small on purpose.
     */
    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getAccessTokenTtl());

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtVerifier.CLAIM_EMAIL, user.getEmail());
        claims.put(JwtVerifier.CLAIM_NAME, user.fullName());
        claims.put(JwtVerifier.CLAIM_ROLE, user.getRole().name());
        claims.put(JwtVerifier.CLAIM_PROFILE_ID, user.getProfileId());
        claims.put(JwtVerifier.CLAIM_TOKEN_TYPE, JwtVerifier.TYPE_ACCESS);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(Codes.secureRef())
                .claims(claims)
                .signWith(key)
                .compact();
    }

    /** A fresh opaque refresh token. Return this to the client; store its hash. */
    public String generateRefreshToken() {
        return Codes.secureRef() + Codes.secureRef();
    }

    /** SHA-256, hex-encoded. Deterministic, so a presented token can be looked up. */
    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 is mandated by the JDK specification.
            throw new IllegalStateException("SHA-256 is unavailable in this JVM", impossible);
        }
    }

    public long accessTokenTtlSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(properties.getRefreshTokenTtl());
    }

    public Instant passwordResetExpiry() {
        return Instant.now().plus(properties.getPasswordResetTtl());
    }
}
