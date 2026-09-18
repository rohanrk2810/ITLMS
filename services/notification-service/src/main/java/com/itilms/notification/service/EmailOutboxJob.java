package com.itilms.notification.service;

import java.time.Instant;
import java.util.List;

import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.itilms.notification.config.NotificationProperties;
import com.itilms.notification.email.EmailComposer;
import com.itilms.notification.email.Mailer;
import com.itilms.notification.entity.EmailOutbox;
import com.itilms.notification.repository.EmailOutboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Sends queued emails, a small batch at a time, retrying failures with a growing delay. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailOutboxJob {

    static final int BATCH_SIZE = 20;

    private final EmailOutboxRepository outbox;
    private final Mailer mailer;
    private final NotificationProperties props;
    private final TransactionTemplate transactions;

    @Scheduled(fixedDelayString = "${itilms.notification.outbox-interval-ms:30000}", initialDelay = 20000)
    public void sendDue() {
        if (!mailer.isEnabled()) {
            return;
        }
        // Carries on while whole batches go through; a failure means the mail
        // server is struggling, and the rest can wait for the next run.
        while (sendBatch() == BATCH_SIZE) {
            log.debug("Outbox batch sent; checking for more");
        }
    }

    /**
     * One transaction per batch: the rows stay locked while they are being sent.
     *
     * @return how many were sent successfully
     */
    int sendBatch() {
        Integer count = transactions.execute(status -> {
            Instant now = Instant.now();
            List<EmailOutbox> due = outbox.claimDue(now, BATCH_SIZE);
            int ok = 0;
            for (EmailOutbox email : due) {
                try {
                    mailer.send(email.getToAddress(),
                            new EmailComposer.Email(email.getSubject(), email.getTextBody(), email.getHtmlBody()));
                    email.markSent(Instant.now());
                    ok++;
                } catch (MailException ex) {
                    email.markFailed(ex.getMessage(), Instant.now(), props.getEmailMaxAttempts());
                    log.warn("Email {} to user {} failed (attempt {}): {}", email.getId(), email.getUserId(),
                            email.getAttempts(), ex.getMessage());
                }
            }
            outbox.saveAll(due);
            return ok;
        });
        return count == null ? 0 : count;
    }
}
