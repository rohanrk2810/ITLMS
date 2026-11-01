package com.itilms.reporting.service.export;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.itilms.reporting.client.BrandingClient;

import lombok.RequiredArgsConstructor;

/**
 * The institute's letterhead for a server-built report, read from the branding the administrator set. Kept for a few
 * minutes so an export does not call identity-service every time. If it cannot be read the report has none.
 */
@Component
@RequiredArgsConstructor
public class InstituteLetterhead {

    private static final long TTL_MILLIS = 5 * 60_000L;

    private final BrandingClient client;
    private volatile Letterhead cached;
    private volatile long fetchedAt;

    public Letterhead get() {
        long now = System.currentTimeMillis();
        Letterhead current = cached;
        if (current != null && now - fetchedAt < TTL_MILLIS) {
            return current;
        }
        BrandingClient.Branding b = null;
        try {
            b = client.branding();
        } catch (RuntimeException unreachable) {
            // Falls through to "no letterhead".
        }
        if (b == null || b.name() == null || b.name().isBlank()) {
            // Not cached, so the next export tries again; an older good answer is better than none.
            return current != null ? current : Letterhead.NONE;
        }
        List<String> lines = new ArrayList<>();
        if (b.address() != null && !b.address().isBlank()) {
            lines.add(b.address().trim());
        }
        String contact = String.join("  |  ", Stream.of(b.contactPhone(), b.contactEmail(), b.website())
                .filter(v -> v != null && !v.isBlank()).map(String::trim).toList());
        if (!contact.isEmpty()) {
            lines.add(contact);
        }
        String tagline = b.tagline() == null || b.tagline().isBlank() ? null : b.tagline().trim();
        cached = new Letterhead(b.name().trim(), tagline, lines);
        fetchedAt = now;
        return cached;
    }
}
