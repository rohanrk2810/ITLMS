package com.itilms.notification.email;

import java.io.UnsupportedEncodingException;

import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.itilms.notification.config.EmailProperties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

/** Sends one email over SMTP. Throws {@link org.springframework.mail.MailException} when it cannot. */
@Component
@RequiredArgsConstructor
public class Mailer {

    private final JavaMailSender sender;
    private final EmailProperties props;
    private final InstituteName institute;

    public boolean isEnabled() {
        return props.isEnabled();
    }

    /** Whether ordinary notifications are emailed too, not just passwords and reset codes. */
    public boolean sendsNotificationEmails() {
        return props.isEnabled() && props.isNotificationsEnabled();
    }

    public void send(String to, EmailComposer.Email email) {
        send(to, email, null, null);
    }

    /** As {@link #send(String, EmailComposer.Email)}, with one PDF attached when {@code attachment} is not null. */
    public void send(String to, EmailComposer.Email email, String attachmentName, byte[] attachment) {
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(props.getFrom(), institute.get());
            helper.setTo(to);
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            if (attachment != null && attachment.length > 0) {
                helper.addAttachment(attachmentName == null ? "attachment.pdf" : attachmentName,
                        new org.springframework.core.io.ByteArrayResource(attachment), "application/pdf");
            }
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new MailPreparationException("Could not build the email", ex);
        }
        sender.send(message);
    }
}
