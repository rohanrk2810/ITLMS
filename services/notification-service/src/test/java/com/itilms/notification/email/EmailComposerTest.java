package com.itilms.notification.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.itilms.notification.config.EmailProperties;
import com.itilms.notification.config.NotificationProperties;
import com.itilms.notification.service.Content;

class EmailComposerTest {

    private final EmailComposer composer = new EmailComposer(engine(), props(), new EmailProperties(),
            new InstituteName(null, new EmailProperties()));

    private static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static NotificationProperties props() {
        NotificationProperties props = new NotificationProperties();
        props.setFrontendUrl("https://lms.institute.in/");
        return props;
    }

    @Test
    @DisplayName("The reset link is built here from the token; it never travelled as a URL")
    void resetLink() {
        var email = composer.compose(new Content("PASSWORD_RESET", "Reset your password", "Use the link.",
                "/reset-password"), new Secrets(null, "a+b/c"));

        assertThat(email.html()).contains("https://lms.institute.in/reset-password?token=a%2Bb%2Fc");
        assertThat(email.text()).contains("Choose a new password: https://lms.institute.in/reset-password?token=a%2Bb%2Fc");
    }

    @Test
    @DisplayName("A temporary password appears in the email")
    void temporaryPassword() {
        var email = composer.compose(new Content("ACCOUNT_CREATED", "Your account is ready", "Sign in.", "/login"),
                new Secrets("Temp#4821", null));

        assertThat(email.html()).contains("Temp#4821").contains("https://lms.institute.in/login");
        assertThat(email.text()).contains("Temporary password: Temp#4821");
    }

    @Test
    @DisplayName("Text from an announcement is escaped, not run as HTML")
    void escaped() {
        var email = composer.compose(new Content("ANNOUNCEMENT", "Holiday <b>notice</b>",
                "<script>alert(1)</script>", "/announcements/4"), Secrets.NONE);

        assertThat(email.html()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("Only paths inside the app become links, so nothing upstream can point the email elsewhere")
    void noOutsideLinks() {
        assertThat(composer.linkFor("https://evil.example/login", Secrets.NONE)).isNull();
        assertThat(composer.linkFor("//evil.example/login", Secrets.NONE)).isNull();
        assertThat(composer.linkFor("/fees", Secrets.NONE)).isEqualTo("https://lms.institute.in/fees");
    }
}
