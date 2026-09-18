package com.itilms.notification.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.notification.config.NotificationProperties;
import com.itilms.notification.repository.EmailOutboxRepository;
import com.itilms.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Deletes old notifications and sent emails, nightly. Emails still waiting to go are kept. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionJob {

    private final NotificationRepository notifications;
    private final EmailOutboxRepository outbox;
    private final NotificationProperties props;

    @Transactional
    @Scheduled(cron = "${itilms.notification.retention-cron:0 30 3 * * *}")
    public void prune() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(props.getRetentionDays()));
        int removed = notifications.deleteCreatedBefore(cutoff);
        int emails = outbox.deleteFinishedBefore(cutoff);
        if (removed > 0 || emails > 0) {
            log.info("Pruned {} notification(s) and {} email(s) older than {} days", removed, emails,
                    props.getRetentionDays());
        }
    }
}
