package com.itilms.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.itilms.common.config.PublicEndpoints;

/**
 * Service-level beans for identity.
 */
@Configuration
public class IdentityBeans {

    /**
     * BCrypt at strength 12 (Doc S6.1, S12).
     *
     * <p>The cost factor is the point of BCrypt: each increment doubles the work
     * to verify a password, and therefore doubles an attacker's cost per guess
     * against a stolen table. 12 lands around a quarter of a second on current
     * server hardware — unnoticeable on a sign-in, punishing at scale.
     *
     * <p>The strength is encoded in every hash, so raising it later does not
     * invalidate existing passwords: old hashes keep verifying at their original
     * cost and are re-hashed at the new one next time their owner signs in.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Endpoints reachable without a token.
     *
     * <p>Only the ones that cannot require authentication: you cannot present a
     * token to the endpoint that issues one, and a user who has forgotten their
     * password by definition cannot sign in to reset it. Everything else on this
     * service is authenticated.
     */
    @Bean
    PublicEndpoints identityPublicEndpoints() {
        return () -> new String[]{
                "/api/auth/login",
                "/api/auth/register",
                "/api/auth/refresh",
                "/api/auth/logout",
                "/api/auth/forgot-password",
                "/api/auth/reset-password",
                "/api/public/branding/**"
        };
    }
}
