package com.itilms.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "itilms.notification")
public class NotificationProperties {

    /** In-app notifications and finished emails older than this are deleted. */
    private int retentionDays = 180;

    /** Where links in emails point. Events carry only the path. */
    private String frontendUrl = "http://localhost:5173";

    /** Attempts before an email is marked FAILED. */
    private int emailMaxAttempts = 5;
}
