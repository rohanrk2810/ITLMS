package com.itilms.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.itilms.common.event.PaymentReceiptEmailEvent;
import com.itilms.notification.email.EmailComposer;
import com.itilms.notification.email.Mailer;
import com.itilms.notification.email.Secrets;
import com.itilms.notification.entity.EmailOutbox;
import com.itilms.notification.entity.Recipient;
import com.itilms.notification.repository.EmailOutboxRepository;
import com.itilms.notification.repository.RecipientRepository;

class ReceiptEmailServiceTest {

    private final EmailOutboxRepository outbox = mock(EmailOutboxRepository.class);
    private final RecipientRepository recipients = mock(RecipientRepository.class);
    private final EmailComposer composer = mock(EmailComposer.class);
    private final Mailer mailer = mock(Mailer.class);
    private final ReceiptEmailService service = new ReceiptEmailService(outbox, recipients, composer, mailer);

    private static PaymentReceiptEmailEvent event(byte[] pdf) {
        return new PaymentReceiptEmailEvent("evt-1", Instant.now(), 7L, "RCPT-1", "Payment receipt RCPT-1",
                "Thank you.", "Receipt-RCPT-1.pdf", pdf);
    }

    private Recipient active(String email) {
        Recipient r = mock(Recipient.class);
        when(r.isActive()).thenReturn(true);
        when(r.getEmail()).thenReturn(email);
        when(r.getUserId()).thenReturn(7L);
        when(recipients.findById(7L)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void queuesTheEmailWithThePdfAttached() {
        when(mailer.isEnabled()).thenReturn(true);
        active("asha@x.in");
        when(composer.compose(any(), any(Secrets.class))).thenReturn(new EmailComposer.Email("S", "text", "<p>html</p>"));

        service.queue(event(new byte[]{1, 2, 3}));

        ArgumentCaptor<EmailOutbox> saved = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(outbox).save(saved.capture());
        assertThat(saved.getValue().getToAddress()).isEqualTo("asha@x.in");
        assertThat(saved.getValue().getAttachmentName()).isEqualTo("Receipt-RCPT-1.pdf");
        assertThat(saved.getValue().getAttachment()).containsExactly(1, 2, 3);
        assertThat(saved.getValue().getEventId()).isEqualTo("evt-1");
    }

    @Test
    void aRedeliveredEventIsNotQueuedTwice() {
        when(mailer.isEnabled()).thenReturn(true);
        when(outbox.existsByEventId("evt-1")).thenReturn(true);

        service.queue(event(new byte[]{1}));

        verify(outbox, never()).save(any());
    }

    @Test
    void nothingIsQueuedWhenMailIsOffOrThereIsNoAddress() {
        when(mailer.isEnabled()).thenReturn(false);
        service.queue(event(new byte[]{1}));

        when(mailer.isEnabled()).thenReturn(true);
        active(" ");
        service.queue(event(new byte[]{1}));

        verify(outbox, never()).save(any());
    }
}
