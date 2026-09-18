package com.itilms.identity.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.ProfileLinkedEvent;
import com.itilms.identity.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Copies the student or trainer id onto the user row when admission-service
 * creates a profile.
 *
 * <p>This is the write half of the arrangement described on
 * {@link ProfileLinkedEvent}: identity-service keeps a local copy of a fact
 * another service owns, so that issuing a token never requires a network call.
 *
 * <p>Idempotent by construction — the handler sets two columns to the values in
 * the event, so a redelivery writes the same thing twice and Kafka's
 * at-least-once guarantee costs nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileLinkedConsumer {

    private final UserRepository userRepository;

    @KafkaListener(topics = KafkaTopics.PROFILE_LINKED, groupId = "identity-service")
    @Transactional
    public void onProfileLinked(ProfileLinkedEvent event) {
        int updated = userRepository.linkProfile(
                event.userId(), event.profileId(), event.profileCode());

        if (updated == 0) {
            // The account was deleted, or the event arrived before the user row
            // was committed. Neither is worth failing the listener over: the
            // profile still exists in admission-service, and the link is
            // re-established the next time the profile is saved.
            log.warn("ProfileLinkedEvent for unknown user {} (profile {} {})",
                    event.userId(), event.profileType(), event.profileId());
            return;
        }

        log.info("Linked {} profile {} ({}) to user {}",
                event.profileType(), event.profileId(), event.profileCode(), event.userId());
    }
}
