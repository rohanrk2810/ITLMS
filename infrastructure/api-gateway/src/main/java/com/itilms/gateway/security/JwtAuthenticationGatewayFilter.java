package com.itilms.gateway.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Stops unauthenticated traffic at the edge.
 *
 * <p>Rejecting a bad token here means a broken or expired session never reaches
 * a service, never opens a database connection, and never occupies a worker
 * thread. It also gives the client one consistent 401 shape regardless of which
 * of the twelve services it was aiming at.
 *
 * <p>On success the verified claims are copied into {@code X-User-*} headers.
 * Those headers are a convenience for logging and tracing, <em>not</em> an
 * authorization input: services derive identity from the token itself, so a
 * forged header on a request that bypassed the gateway changes nothing.
 * The gateway strips any inbound {@code X-User-*} headers for exactly that
 * reason — a client must not be able to suggest who it is.
 */
@Slf4j
@Component
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    private static final String BEARER = "Bearer ";
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    static final String HEADER_USER_ID = "X-User-Id";
    static final String HEADER_USER_EMAIL = "X-User-Email";
    static final String HEADER_USER_ROLE = "X-User-Role";
    static final String HEADER_PROFILE_ID = "X-Profile-Id";

    private final GatewayJwtProperties properties;
    private final SecretKey key;

    public JwtAuthenticationGatewayFilter(GatewayJwtProperties properties) {
        this.properties = properties;
        this.key = buildKey(properties.getJwtSecret());
    }

    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "itilms.gateway.jwt-secret is not set. The gateway cannot verify tokens without it.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException notBase64) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must decode to at least 32 bytes; got " + bytes.length);
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Never let a caller inject its own identity headers.
        ServerHttpRequest sanitised = request.mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_EMAIL);
                    headers.remove(HEADER_USER_ROLE);
                    headers.remove(HEADER_PROFILE_ID);
                })
                .build();

        if (isPublic(path) || request.getMethod().name().equals("OPTIONS")) {
            return chain.filter(exchange.mutate().request(sanitised).build());
        }

        String token = bearerToken(request);
        if (token == null) {
            return unauthorized(exchange, "A bearer token is required to call this endpoint");
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .clockSkewSeconds(properties.getClockSkewSeconds())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected token for {}: {}", path, ex.getMessage());
            return unauthorized(exchange, "Your session is invalid or has expired. Please sign in again.");
        }

        if (!"ACCESS".equals(claims.get("typ", String.class))) {
            return unauthorized(exchange, "A refresh token cannot be used to call the API");
        }

        Object profileId = claims.get("pid");
        ServerHttpRequest enriched = sanitised.mutate()
                .header(HEADER_USER_ID, String.valueOf(claims.getSubject()))
                .header(HEADER_USER_EMAIL, nullSafe(claims.get("email", String.class)))
                .header(HEADER_USER_ROLE, nullSafe(claims.get("role", String.class)))
                .header(HEADER_PROFILE_ID, profileId == null ? "" : String.valueOf(profileId))
                .build();

        return chain.filter(exchange.mutate().request(enriched).build());
    }

    private boolean isPublic(String path) {
        List<String> patterns = properties.getPublicPaths();
        for (String pattern : patterns) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private String bearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            String value = header.substring(BEARER.length()).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"timestamp":"%s","status":401,"error":"Unauthorized",\
                "code":"AUTHENTICATION_REQUIRED","message":"%s","path":"%s"}"""
                .formatted(java.time.Instant.now(), message, exchange.getRequest().getURI().getPath());

        var buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Runs before routing but after the correlation-id filter, so a rejected
     * request still carries a traceable id in the access log.
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
