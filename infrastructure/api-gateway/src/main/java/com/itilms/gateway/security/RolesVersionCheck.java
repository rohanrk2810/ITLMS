package com.itilms.gateway.security;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Notices a token that was issued before the person's duties last changed.
 *
 * <p>identity-service writes {@code rv:<userId>} to Redis whenever an administrator gives or withdraws extra duties,
 * and puts the same number in every token it issues. A token whose number is lower than the stored one carries the old
 * duties. The gateway answers it with 401 {@code PERMISSIONS_CHANGED}; the web app renews the token (which reads the
 * duties afresh) and repeats the request, so the change applies at once and nobody has to sign in again.
 *
 * <p>If Redis cannot be reached the check is skipped (the token's own expiry still limits how long old duties last),
 * rather than locking everyone out because a cache is down.
 */
@Component
public class RolesVersionCheck {

    /** Identity writes the same key. */
    static final String KEY_PREFIX = "rv:";
    private static final Duration TIMEOUT = Duration.ofMillis(500);
    private static final Logger log = LoggerFactory.getLogger(RolesVersionCheck.class);

    private final ReactiveStringRedisTemplate redis;

    public RolesVersionCheck(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    /** True when the stored number is higher than the one in the token. A person never changed has no stored number. */
    public Mono<Boolean> isStale(String userId, int tokenVersion) {
        return redis.opsForValue().get(KEY_PREFIX + userId)
                .timeout(TIMEOUT)
                .map(RolesVersionCheck::parse)
                .map(current -> current > tokenVersion)
                .defaultIfEmpty(false)
                .onErrorResume(error -> {
                    log.warn("Could not read the roles version of user {}; not checking this request", userId, error);
                    return Mono.just(false);
                });
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
