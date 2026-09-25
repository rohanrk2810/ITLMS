package com.itilms.gateway.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Edge authentication settings.
 *
 * <p>{@code publicPaths} is the gateway's allow-list of anonymous routes. It is
 * intentionally explicit and short — the public surface of an institute system
 * is the course catalog, the enquiry form, login, and certificate verification.
 * Everything else requires a token before it reaches a service.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "itilms.gateway")
public class GatewayJwtProperties {

    /** Shared HMAC secret; must match identity-service's signing key. */
    private String jwtSecret;

    private String issuer = "it-ilms";

    private long clockSkewSeconds = 30;

    /** Ant-style patterns served without authentication. */
    private List<String> publicPaths = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/public/**",
            "/api/courses/public/**",
            "/api/certificates/verify/**",
            "/api/leads/enquiry",
            "/api/liveclass/webhook/**",
            "/actuator/health/**",
            "/v3/api-docs/**",
            // The gateway's Swagger page loads each service's docs through its route prefix.
            "/api/*/v3/api-docs",
            "/api/*/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    );
}
