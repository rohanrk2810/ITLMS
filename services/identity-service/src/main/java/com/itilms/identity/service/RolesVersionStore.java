package com.itilms.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Tells the gateway, at once, that a person's duties changed. The gateway compares the number in a token with the one
 * stored here and sends an older token to be renewed, so the new duties (or the loss of them) apply on the very next
 * request. Redis is the same one the gateway already uses for rate limiting.
 *
 * <p>If Redis is unreachable the change is still saved and still applies when the token is next renewed (within the
 * token's life); {@link #publish} says so by returning false.
 */
@Component
public class RolesVersionStore {

    /** The gateway reads the same key. */
    public static final String KEY_PREFIX = "rv:";

    private static final Logger log = LoggerFactory.getLogger(RolesVersionStore.class);

    private final StringRedisTemplate redis;

    public RolesVersionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** @return true when the gateway has been told. */
    public boolean publish(Long userId, int version) {
        try {
            redis.opsForValue().set(KEY_PREFIX + userId, Integer.toString(version));
            return true;
        } catch (RuntimeException e) {
            log.error("Could not tell the gateway that user {} now has roles version {}; the change applies when the "
                    + "token is next renewed", userId, version, e);
            return false;
        }
    }
}
