package com.itilms.certificate.service;

import org.springframework.stereotype.Component;

import com.itilms.certificate.client.BrandingClient;
import com.itilms.certificate.config.CertificateProperties;

/**
 * What a printed certificate says about the institute: the name, signatory and logo the admin
 * set under Branding, kept for a minute. If identity-service cannot be reached the configured
 * name is used and no logo is printed, so a certificate can still be produced.
 */
@Component
public class InstituteBranding {

    /** The values to print. {@code logo} is null when none is set or it could not be read. */
    public record Info(String name, String signatoryName, String signatoryTitle, byte[] logo) {
    }

    private static final long TTL_MILLIS = 60_000;

    private final BrandingClient client;
    private final CertificateProperties props;

    private volatile Info cached;
    private volatile long fetchedAt;

    public InstituteBranding(BrandingClient client, CertificateProperties props) {
        this.client = client;
        this.props = props;
    }

    public Info get() {
        long now = System.currentTimeMillis();
        Info current = cached;
        if (current != null && now - fetchedAt < TTL_MILLIS) {
            return current;
        }
        try {
            BrandingClient.Branding b = client == null ? null : client.branding();
            if (b != null && b.name() != null && !b.name().isBlank()) {
                byte[] logo = null;
                if (b.logoUrl() != null) {
                    var response = client.logo();
                    logo = response == null ? null : response.getBody();
                }
                cached = new Info(b.name(),
                        b.signatoryName() != null ? b.signatoryName() : props.getSignatoryName(),
                        b.signatoryTitle(), logo);
                fetchedAt = now;
                return cached;
            }
        } catch (RuntimeException ignored) {
            // fall through to the last good value, or the configured one
        }
        return current != null ? current : new Info(props.getInstituteName(), props.getSignatoryName(), null, null);
    }
}
