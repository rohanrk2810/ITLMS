package com.itilms.notification.email;

import org.springframework.stereotype.Component;

import com.itilms.notification.client.BrandingClient;
import com.itilms.notification.config.EmailProperties;

/**
 * The institute's name for emails: what the admin set under Branding, kept for a minute so a
 * burst of emails is not a burst of calls. If identity-service cannot be reached the
 * configured sender name is used, so an outage there never stops mail.
 */
@Component
public class InstituteName {

    private static final long TTL_MILLIS = 60_000;

    private final BrandingClient client;
    private final EmailProperties props;

    private volatile String cached;
    private volatile long fetchedAt;

    public InstituteName(BrandingClient client, EmailProperties props) {
        this.client = client;
        this.props = props;
    }

    public String get() {
        long now = System.currentTimeMillis();
        if (cached != null && now - fetchedAt < TTL_MILLIS) {
            return cached;
        }
        try {
            BrandingClient.Branding b = client == null ? null : client.branding();
            if (b != null && b.name() != null && !b.name().isBlank()) {
                cached = b.name();
                fetchedAt = now;
                return cached;
            }
        } catch (RuntimeException ignored) {
            // fall through to the configured name
        }
        return cached != null ? cached : props.getFromName();
    }
}
