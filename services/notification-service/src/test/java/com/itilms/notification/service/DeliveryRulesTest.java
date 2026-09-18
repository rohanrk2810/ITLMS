package com.itilms.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;

import com.itilms.common.event.JobPostedEvent;
import com.itilms.common.event.NotificationRequestedEvent;
import com.itilms.notification.email.EmailComposer;
import com.itilms.notification.email.Mailer;
import com.itilms.notification.email.Secrets;
import com.itilms.notification.entity.EmailOutbox;
import com.itilms.notification.entity.Recipient;
import com.itilms.notification.repository.EmailOutboxRepository;
import com.itilms.notification.repository.NotificationRepository;
import com.itilms.notification.repository.RecipientRepository;

class DeliveryRulesTest {

    private static final EmailComposer.Email EMAIL = new EmailComposer.Email("s", "t", "h");

    @Nested
    @DisplayName("Writing notifications and queueing email")
    class Dispatcher {

        private final NotificationRepository notifications = mock(NotificationRepository.class);
        private final RecipientRepository recipients = mock(RecipientRepository.class);
        private final EmailOutboxRepository outbox = mock(EmailOutboxRepository.class);
        private final EmailComposer composer = mock(EmailComposer.class);
        private final Mailer mailer = mock(Mailer.class);
        private final NotificationDispatcher dispatcher =
                new NotificationDispatcher(notifications, recipients, outbox, composer, mailer);
        private final Content content = new Content("FEE_DUE", "Fee due", "Pay by Friday", "/fees");

        @SuppressWarnings("unchecked")
        private List<EmailOutbox> queued() {
            ArgumentCaptor<List<EmailOutbox>> rows = ArgumentCaptor.forClass(List.class);
            verify(outbox).saveAll(rows.capture());
            return rows.getValue();
        }

        @Test
        @DisplayName("An event delivered twice by Kafka notifies and emails nobody a second time")
        void repeatIsSilent() {
            when(mailer.isEnabled()).thenReturn(true);
            when(notifications.insertIfAbsent(anyString(), anyLong(), any(), any(), any(), any())).thenReturn(0);

            assertThat(dispatcher.deliver("evt-1", List.of(1L, 2L), content, true)).isZero();
            verify(outbox, never()).saveAll(any());
        }

        @Test
        @DisplayName("Only people with an address and an active account get an email")
        void emailsActiveAddressesOnly() {
            when(mailer.isEnabled()).thenReturn(true);
            when(composer.compose(any(), eq(Secrets.NONE))).thenReturn(EMAIL);
            when(notifications.insertIfAbsent(anyString(), anyLong(), any(), any(), any(), any())).thenReturn(1);
            List<Recipient> people = List.of(
                    recipient(1L, "a@x.in", true), recipient(2L, null, true), recipient(3L, "c@x.in", false));
            when(recipients.findAllById(anyCollection())).thenReturn(people);

            assertThat(dispatcher.deliver("evt-1", List.of(1L, 2L, 3L), content, true)).isEqualTo(3);
            assertThat(queued()).extracting(EmailOutbox::getToAddress).containsExactly("a@x.in");
        }

        @Test
        @DisplayName("While email is off nothing is queued, so switching it on later sends no stale backlog")
        void noBacklogWhileOff() {
            when(mailer.isEnabled()).thenReturn(false);
            when(notifications.insertIfAbsent(anyString(), anyLong(), any(), any(), any(), any())).thenReturn(1);

            assertThat(dispatcher.deliver("evt-1", List.of(1L), content, true)).isEqualTo(1);
            verify(outbox, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("Emails carrying a password or a reset link")
    class SecretEmails {

        private final AudienceResolver audiences = mock(AudienceResolver.class);
        private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        private final EmailComposer composer = mock(EmailComposer.class);
        private final Mailer mailer = mock(Mailer.class);
        private final RecipientRepository recipients = mock(RecipientRepository.class);
        private final NotificationIntake intake = new NotificationIntake(audiences, dispatcher, composer, mailer, recipients);

        private NotificationRequestedEvent reset(List<Long> to, Long batchId) {
            return new NotificationRequestedEvent("evt-9", Instant.now(), to, batchId, null, "PASSWORD_RESET",
                    "Reset your password", "Use the link below.", "/reset-password", true,
                    Map.of("resetToken", "raw-token", "email", "asha@x.in"));
        }

        @Test
        @DisplayName("The email goes out directly; the stored notification and the queue never see the token")
        void sentDirectlyNotStored() {
            when(mailer.isEnabled()).thenReturn(true);
            when(audiences.forRequest(any())).thenReturn(Set.of(5L));
            when(composer.compose(any(), any())).thenReturn(EMAIL);

            intake.handle(reset(List.of(5L), null));

            ArgumentCaptor<Secrets> secrets = ArgumentCaptor.forClass(Secrets.class);
            verify(composer).compose(any(), secrets.capture());
            assertThat(secrets.getValue().resetToken()).isEqualTo("raw-token");
            verify(mailer).send("asha@x.in", EMAIL);

            ArgumentCaptor<Content> stored = ArgumentCaptor.forClass(Content.class);
            // email=false: the in-app row is written, but nothing joins the outbox
            verify(dispatcher).deliver(eq("evt-9"), any(), stored.capture(), eq(false));
            assertThat(stored.getValue().toString()).doesNotContain("raw-token");
        }

        @Test
        @DisplayName("The email is sent before the notification is written, so a failed send is retried in full")
        void emailFirst() {
            when(mailer.isEnabled()).thenReturn(true);
            when(audiences.forRequest(any())).thenReturn(Set.of(5L));
            when(composer.compose(any(), any())).thenReturn(EMAIL);

            intake.handle(reset(List.of(5L), null));

            var order = inOrder(mailer, dispatcher);
            order.verify(mailer).send(any(), any());
            order.verify(dispatcher).deliver(any(), any(), any(), eq(false));
        }

        @Test
        @DisplayName("When the send fails the event fails too, and Kafka delivers it again")
        void failureIsRetried() {
            when(mailer.isEnabled()).thenReturn(true);
            when(composer.compose(any(), any())).thenReturn(EMAIL);
            doThrow(new MailSendException("SMTP down")).when(mailer).send(any(), any());

            assertThatThrownBy(() -> intake.handle(reset(List.of(5L), null))).isInstanceOf(MailSendException.class);
            verify(dispatcher, never()).deliver(any(), any(), any(), eq(false));
        }

        @Test
        @DisplayName("Credentials addressed to a group are never emailed")
        void neverToAGroup() {
            when(mailer.isEnabled()).thenReturn(true);

            intake.handle(reset(List.of(5L, 6L), null));
            intake.handle(reset(List.of(5L), 12L));

            verify(mailer, never()).send(any(), any());
        }

        @Test
        @DisplayName("Logging the secrets by mistake prints nothing useful")
        void redacted() {
            assertThat(new Secrets("Temp#123", null).toString()).doesNotContain("Temp#123");
        }
    }

    @Nested
    @DisplayName("Job openings")
    class Jobs {

        private final AudienceResolver audiences = mock(AudienceResolver.class);
        private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        private final NotificationIntake intake = new NotificationIntake(audiences, dispatcher,
                mock(EmailComposer.class), mock(Mailer.class), mock(RecipientRepository.class));

        @Test
        @DisplayName("Students of the eligible courses are told, by email too, with the deadline")
        void studentsTold() {
            when(audiences.forJob(List.of(5L))).thenReturn(Set.of(11L, 12L));

            intake.handle(new JobPostedEvent("evt-3", Instant.now(), 40L, 2L, "Infosys", "Java developer", "4 LPA",
                    LocalDate.of(2026, 10, 1), List.of(5L), null, null));

            ArgumentCaptor<Content> content = ArgumentCaptor.forClass(Content.class);
            verify(dispatcher).deliver(eq("evt-3"), eq(Set.of(11L, 12L)), content.capture(), eq(true));
            assertThat(content.getValue().title()).isEqualTo("Java developer at Infosys");
            assertThat(content.getValue().message()).contains("1 Oct 2026");
            assertThat(content.getValue().actionUrl()).isEqualTo("/jobs/40");
        }
    }

    @Nested
    @DisplayName("Retrying failed emails")
    class Retry {

        @Test
        @DisplayName("The wait doubles after each failure, and the email is given up on at the limit")
        void backoff() {
            EmailOutbox email = EmailOutbox.builder().toAddress("a@x.in").subject("s").textBody("t").htmlBody("h").build();
            Instant now = Instant.parse("2026-09-18T10:00:00Z");

            email.markFailed("down", now, 3);
            assertThat(email.getNextAttemptAt()).isEqualTo(now.plusSeconds(120));
            email.markFailed("down", now, 3);
            assertThat(email.getNextAttemptAt()).isEqualTo(now.plusSeconds(240));
            assertThat(email.getStatus()).isEqualTo(EmailOutbox.PENDING);
            email.markFailed("down", now, 3);
            assertThat(email.getStatus()).isEqualTo(EmailOutbox.FAILED);
        }

        @Test
        @DisplayName("Content that would not fit its columns is shortened rather than rejected")
        void contentTrimmed() {
            Content content = new Content(null, "x".repeat(300), "m", "/" + "a".repeat(600));
            assertThat(content.type()).isEqualTo("GENERAL");
            assertThat(content.title()).hasSize(200);
            assertThat(content.actionUrl()).isNull();
        }
    }

    private static Recipient recipient(Long id, String email, boolean active) {
        Recipient r = mock(Recipient.class);
        when(r.getUserId()).thenReturn(id);
        when(r.getEmail()).thenReturn(email);
        when(r.isActive()).thenReturn(active);
        return r;
    }
}
