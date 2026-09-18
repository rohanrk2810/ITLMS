package com.itilms.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import reactor.core.publisher.Mono;

/**
 * Who a rate limit counts against.
 *
 * <p>Doc S12 asks for login and password-reset throttling. Those endpoints are
 * anonymous by definition, so the bucket has to be keyed on the client address —
 * there is no user id yet, and keying on the submitted email would let an
 * attacker lock a victim out of their own account by spamming failures.
 *
 * <p>Behind a load balancer the socket address is the balancer's. The resolver
 * therefore prefers {@code X-Forwarded-For}, which is only trustworthy because
 * the proxy in front of the gateway is configured to overwrite it. Accepting a
 * client-supplied value with no such proxy would make the limit trivially
 * bypassable.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    @Primary
    KeyResolver clientAddressKeyResolver() {
        return exchange -> {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return Mono.just((comma > 0 ? forwarded.substring(0, comma) : forwarded).trim());
            }
            var remote = exchange.getRequest().getRemoteAddress();
            return Mono.just(remote == null ? "unknown" : remote.getAddress().getHostAddress());
        };
    }

    /**
     * For authenticated routes worth limiting per account rather than per
     * address — bulk exports, report generation — so one heavy user cannot
     * starve everyone sharing an office IP.
     */
    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            return Mono.just(userId == null || userId.isBlank() ? "anonymous" : userId);
        };
    }
}
