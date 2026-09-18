package com.itilms.assessment.service;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scores attempts whose time ran out without a submission.
 *
 * <p>Closing the tab is the commonest way a test ends. Without this, such an
 * attempt stays open indefinitely: it uses up one of the student's attempts,
 * shows no score, and never reaches the results sheet or the certificate check.
 * Each is scored on whatever the student had saved.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttemptExpiryJob {

    private final AttemptScorer scorer;

    @Scheduled(fixedDelayString = "${itilms.assessment.expiry-sweep-ms:60000}",
            initialDelayString = "${itilms.assessment.expiry-initial-delay-ms:30000}")
    public void sweep() {
        int expired = scorer.expireOverdue(Instant.now());
        if (expired > 0) {
            log.info("Scored {} attempt(s) whose time ran out", expired);
        }
    }
}
