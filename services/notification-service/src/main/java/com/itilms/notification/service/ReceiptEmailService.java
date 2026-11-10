package com.itilms.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.PaymentReceiptEmailEvent;
import com.itilms.notification.email.EmailComposer;
import com.itilms.notification.email.Mailer;
import com.itilms.notification.email.Secrets;
import com.itilms.notification.entity.EmailOutbox;
import com.itilms.notification.entity.Recipient;
import com.itilms.notification.repository.EmailOutboxRepository;
import com.itilms.notification.repository.RecipientRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Queues the payment receipt email, with the PDF attached.
 *
 * <p>The in-app "Payment received" notice is raised by finance-service as before; this adds the email. It is sent
 * whenever mail is switched on, not only when ordinary notification email is on, because a receipt is a record the
 * student is owed. A redelivered event is ignored through {@code event_id}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptEmailService {

    private final EmailOutboxRepository outbox;
    private final RecipientRepository recipients;
    private final EmailComposer composer;
    private final Mailer mailer;

    @Transactional
    public void queue(PaymentReceiptEmailEvent event) {
        if (!mailer.isEnabled() || event.userId() == null || event.pdf() == null || event.pdf().length == 0) {
            return;
        }
        if (outbox.existsByEventId(event.eventId())) {
            return;
        }
        Recipient recipient = recipients.findById(event.userId()).orElse(null);
        if (recipient == null || !recipient.isActive() || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            log.info("No email address for user {}; receipt {} not emailed", event.userId(), event.receiptNo());
            return;
        }
        EmailComposer.Email email = composer.compose(
                new Content("PAYMENT", event.subject(), event.message(), null), Secrets.NONE);
        outbox.save(EmailOutbox.builder()
                .userId(recipient.getUserId())
                .toAddress(recipient.getEmail())
                .subject(email.subject())
                .textBody(email.text())
                .htmlBody(email.html())
                .attachmentName(event.fileName())
                .attachment(event.pdf())
                .eventId(event.eventId())
                .build());
    }
}
