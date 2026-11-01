package com.itilms.gateway.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** A token issued before a person's duties last changed is stale; everything else goes through. */
class RolesVersionCheckTest {

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
    private final RolesVersionCheck check = new RolesVersionCheck(redis);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void aTokenOlderThanTheStoredNumberIsStale() {
        when(values.get("rv:7")).thenReturn(Mono.just("3"));

        StepVerifier.create(check.isStale("7", 2)).expectNext(true).verifyComplete();
    }

    @Test
    void aTokenWithTheCurrentNumberIsFine() {
        when(values.get("rv:7")).thenReturn(Mono.just("3"));

        StepVerifier.create(check.isStale("7", 3)).expectNext(false).verifyComplete();
    }

    @Test
    void aTokenWithANewerNumberIsFine() {
        when(values.get("rv:7")).thenReturn(Mono.just("3"));

        StepVerifier.create(check.isStale("7", 4)).expectNext(false).verifyComplete();
    }

    @Test
    void aPersonWhoseDutiesWereNeverChangedHasNothingStored() {
        when(values.get("rv:8")).thenReturn(Mono.empty());

        StepVerifier.create(check.isStale("8", 0)).expectNext(false).verifyComplete();
    }

    @Test
    void anOldTokenWithoutTheNumberCountsAsZeroSoAChangeCatchesIt() {
        when(values.get("rv:7")).thenReturn(Mono.just("1"));

        StepVerifier.create(check.isStale("7", 0)).expectNext(true).verifyComplete();
    }

    @Test
    void redisBeingDownDoesNotLockAnyoneOut() {
        when(values.get("rv:7")).thenReturn(Mono.error(new IllegalStateException("redis down")));

        StepVerifier.create(check.isStale("7", 0)).expectNext(false).verifyComplete();
    }

    @Test
    void aSlowRedisIsGivenUpOnQuickly() {
        when(values.get("rv:7")).thenReturn(Mono.just("9").delayElement(Duration.ofSeconds(5)));

        StepVerifier.create(check.isStale("7", 0)).expectNext(false).expectComplete().verify(Duration.ofSeconds(3));
    }

    @Test
    void aStoredValueThatIsNotANumberIsIgnored() {
        when(values.get("rv:7")).thenReturn(Mono.just("not a number"));

        StepVerifier.create(check.isStale("7", 0)).expectNext(false).verifyComplete();
    }
}
