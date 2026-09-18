package com.itilms.notification.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.notification.email.EmailComposer;
import com.itilms.notification.email.Mailer;
import com.itilms.notification.email.Secrets;
import com.itilms.notification.entity.EmailOutbox;
import com.itilms.notification.entity.Recipient;
import com.itilms.notification.repository.EmailOutboxRepository;
import com.itilms.notification.repository.NotificationRepository;
import com.itilms.notification.repository.RecipientRepository;

import lombok.RequiredArgsConstructor;

/**
 * Writes notifications and queues their emails, in one transaction.
 *
 * <p>Either both happen or neither does, and the email is sent later by
 * {@link EmailOutboxJob}. A slow or broken mail server therefore never holds
 * up, or undoes, the in-app notification.
 */
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationRepository notifications;
    private final RecipientRepository recipients;
    private final EmailOutboxRepository outbox;
    private final EmailComposer composer;
    private final Mailer mailer;

    /**
     * @param eventId what caused this; a repeat of the same event reaches nobody twice
     * @param email   whether to email as well. Ignored while email is switched off,
     *                so switching it on later does not release a backlog of stale mail.
     * @return how many people were newly notified
     */
    @Transactional
    public int deliver(String eventId, Collection<Long> userIds, Content content, boolean email) {
        List<Long> newlyNotified = new ArrayList<>();
        for (Long userId : userIds) {
            if (notifications.insertIfAbsent(eventId, userId, content.type(), content.title(),
                    content.message(), content.actionUrl()) > 0) {
                newlyNotified.add(userId);
            }
        }
        if (email && mailer.isEnabled() && !newlyNotified.isEmpty()) {
            queueEmails(newlyNotified, content);
        }
        return newlyNotified.size();
    }

    private void queueEmails(List<Long> userIds, Content content) {
        // Rendered once: the email is the same for everyone who gets it.
        EmailComposer.Email email = composer.compose(content, Secrets.NONE);
        List<EmailOutbox> rows = new ArrayList<>();
        for (Recipient recipient : recipients.findAllById(userIds)) {
            if (recipient.isActive() && recipient.getEmail() != null && !recipient.getEmail().isBlank()) {
                rows.add(EmailOutbox.builder()
                        .userId(recipient.getUserId())
                        .toAddress(recipient.getEmail())
                        .subject(email.subject())
                        .textBody(email.text())
                        .htmlBody(email.html())
                        .build());
            }
        }
        outbox.saveAll(rows);
    }
}
