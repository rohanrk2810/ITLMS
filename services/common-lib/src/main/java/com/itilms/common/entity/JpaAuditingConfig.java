package com.itilms.common.entity;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;

/**
 * Wires {@code @CreatedBy} / {@code @LastModifiedBy} to the authenticated caller.
 *
 * <p>Services import this explicitly rather than getting it through
 * auto-configuration, because {@code @EnableJpaAuditing} must sit alongside the
 * service's own JPA setup, and only services with a database want it at all.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")
public class JpaAuditingConfig {

    /**
     * Background work — Kafka consumers, scheduled jobs — runs with no security
     * context. Those writes are attributed to no user rather than to whoever
     * happened to trigger the chain, which keeps "who changed this" honest.
     */
    @Bean
    AuditorAware<Long> securityAuditorAware() {
        return () -> SecurityUtils.currentPrincipal().map(AppPrincipal::userId);
    }
}
