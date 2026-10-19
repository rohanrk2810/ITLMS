package com.itilms.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** The institute's sending identity, and the switch that turns email on. */
@Getter
@Setter
@ConfigurationProperties(prefix = "itilms.mail")
public class EmailProperties {

    /** Email can be sent at all. The password and reset-code emails need only this. */
    private boolean enabled;

    /**
     * Also email ordinary notifications (fees, results, announcements, job postings).
     * Off by default: the institute uses real email only for new-account passwords and password reset,
     * and everything else stays in-app.
     */
    private boolean notificationsEnabled;

    private String from = "no-reply@itinstitute.local";

    private String fromName = "IT Institute";
}
