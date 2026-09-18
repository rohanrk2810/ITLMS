package com.itilms.notification.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.itilms.common.event.JobPostedEvent;
import com.itilms.common.event.NotificationRequestedEvent;
import com.itilms.notification.email.EmailComposer;
import com.itilms.notification.email.Mailer;
import com.itilms.notification.email.Secrets;
import com.itilms.notification.repository.RecipientRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Turns incoming events into notifications. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationIntake {

    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final AudienceResolver audiences;
    private final NotificationDispatcher dispatcher;
    private final EmailComposer composer;
    private final Mailer mailer;
    private final RecipientRepository recipients;

    /**
     * Doc S16. An email carrying a temporary password or a reset link is sent
     * straight away and never queued, because the queue is a table and the
     * secret must not be stored. If that send fails the exception reaches
     * Kafka, which delivers the event again; the in-app notification is written
     * only after the email has gone, so the retry sends it rather than
     * skipping it as a repeat.
     */
    public void handle(NotificationRequestedEvent event) {
        Content content = new Content(event.type(), event.title(), event.message(), event.actionUrl());
        Secrets secrets = Secrets.from(event.metadata());
        if (!secrets.isEmpty()) {
            sendSecretEmail(event, content, secrets);
        }
        dispatcher.deliver(event.eventId(), audiences.forRequest(event), content,
                event.email() && secrets.isEmpty());
    }

    /** Doc S7.4 "Eligible students notified". Eligibility itself is checked when they apply. */
    public void handle(JobPostedEvent event) {
        String deadline = event.applicationDeadline() == null ? null : DEADLINE.format(event.applicationDeadline());
        String message = (event.packageOffered() == null ? "" : "Package: " + event.packageOffered() + ". ")
                + (deadline == null ? "Open the job to see whether you can apply."
                        : "Applications close on " + deadline + ".");
        Content content = new Content("JOB_POSTED", event.title() + " at " + event.companyName(),
                message, "/jobs/" + event.jobId());
        int reached = dispatcher.deliver(event.eventId(), audiences.forJob(event.eligibleCourseIds()), content, true);
        log.info("Job {} announced to {} student(s)", event.jobId(), reached);
    }

    private void sendSecretEmail(NotificationRequestedEvent event, Content content, Secrets secrets) {
        List<Long> to = event.recipientUserIds() == null ? List.of() : event.recipientUserIds();
        if (to.size() != 1 || event.batchId() != null || event.role() != null) {
            // A password is for one person. Sending it to a group would be a
            // breach, so the request is refused outright rather than guessed at.
            log.error("Refusing to email a {} carrying credentials to more than one person (event {})",
                    content.type(), event.eventId());
            return;
        }
        Long userId = to.get(0);
        if (!mailer.isEnabled()) {
            log.warn("Email is switched off, so the {} email for user {} was not sent. "
                    + "Switch it on (MAIL_ENABLED) or reset the password again once it is.", content.type(), userId);
            return;
        }
        String address = addressFor(userId, event.metadata());
        if (address == null) {
            log.error("No email address for user {}, so the {} email was not sent", userId, content.type());
            return;
        }
        EmailComposer.Email email = composer.compose(content, secrets);
        mailer.send(address, email);
        log.info("Sent the {} email to user {}", content.type(), userId);
    }

    /** identity-service sends the address along: the user may be too new to be in the local copy. */
    private String addressFor(Long userId, Map<String, String> metadata) {
        String fromEvent = metadata == null ? null : metadata.get("email");
        if (fromEvent != null && !fromEvent.isBlank()) {
            return fromEvent;
        }
        return recipients.findById(userId).map(r -> r.getEmail()).orElse(null);
    }
}
