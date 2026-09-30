package com.itilms.notification.email;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import com.itilms.notification.config.EmailProperties;
import com.itilms.notification.config.NotificationProperties;
import com.itilms.notification.service.Content;

import lombok.RequiredArgsConstructor;

/** Turns a notification into an email: a subject, a plain-text body and an HTML body. */
@Component
@RequiredArgsConstructor
public class EmailComposer {

    public record Email(String subject, String text, String html) {
    }

    private final ITemplateEngine templates;
    private final NotificationProperties props;
    private final EmailProperties mail;
    private final InstituteName institute;

    public Email compose(Content content, Secrets secrets) {
        String name = institute.get();
        String link = linkFor(content.actionUrl(), secrets);
        String label = secrets.resetToken() != null ? "Choose a new password" : "Open " + name;

        Context context = new Context();
        context.setVariable("institute", name);
        context.setVariable("title", content.title());
        context.setVariable("message", content.message());
        context.setVariable("link", link);
        context.setVariable("linkLabel", label);
        context.setVariable("temporaryPassword", secrets.temporaryPassword());
        String html = templates.process("email/notification", context);

        StringBuilder text = new StringBuilder(content.title()).append("\n\n");
        if (content.message() != null) {
            text.append(content.message()).append("\n\n");
        }
        if (secrets.temporaryPassword() != null) {
            text.append("Temporary password: ").append(secrets.temporaryPassword()).append("\n\n");
        }
        if (link != null) {
            text.append(label).append(": ").append(link).append("\n\n");
        }
        text.append("- ").append(name);
        return new Email(content.title(), text.toString(), html);
    }

    /**
     * Only paths inside the app become links. An event carrying a full URL
     * gets no link at all, so nothing upstream can make the institute's
     * email point somewhere else.
     */
    String linkFor(String actionUrl, Secrets secrets) {
        String base = props.getFrontendUrl().replaceAll("/+$", "");
        if (secrets.resetToken() != null) {
            return base + "/reset-password?token=" + URLEncoder.encode(secrets.resetToken(), StandardCharsets.UTF_8);
        }
        if (actionUrl == null || !actionUrl.startsWith("/") || actionUrl.startsWith("//")) {
            return null;
        }
        return base + actionUrl;
    }
}
