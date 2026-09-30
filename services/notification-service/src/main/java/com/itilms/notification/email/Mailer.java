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

    public void send(String to, EmailComposer.Email email) {
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(props.getFrom(), institute.get());
            helper.setTo(to);
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new MailPreparationException("Could not build the email", ex);
        }
        sender.send(message);
    }
}
