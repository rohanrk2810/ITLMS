package com.itilms.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** The institute's sending identity, and the switch that turns email on. */
@Getter
@Setter
@ConfigurationProperties(prefix = "itilms.mail")
public class EmailProperties {

    private boolean enabled;

    private String from = "no-reply@itinstitute.local";

    private String fromName = "IT Institute";
}
