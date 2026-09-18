package com.itilms.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * JWT settings shared by the issuer (identity-service) and every verifier.
 *
 * <p>The secret is never hard-coded: it arrives from the environment through the
 * config server (Doc S12: "Use environment variables/secrets for ... JWT secret").
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "itilms.jwt")
public class JwtProperties {

    /** HMAC-SHA key, base64 or raw. Must be at least 32 bytes after decoding. */
    private String secret;

    /** Token issuer claim; verifiers reject tokens minted by anyone else. */
    private String issuer = "it-ilms";

    /** Doc S12 asks for a short-lived access token. */
    private Duration accessTokenTtl = Duration.ofMinutes(30);

    /** Refresh token lifetime; the hash is persisted so it can be revoked. */
    private Duration refreshTokenTtl = Duration.ofDays(7);

    /** Password reset link lifetime. */
    private Duration passwordResetTtl = Duration.ofHours(2);

    /** Tolerance for clock drift between services. */
    private Duration clockSkew = Duration.ofSeconds(30);
}
