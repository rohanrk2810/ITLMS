package com.itilms.notification.email;

import java.util.Map;

/**
 * A temporary password, password-reset token or one-time code that arrived with an event.
 *
 * <p>It goes into one email and nowhere else: not a table, not the email
 * outbox, not a log line. {@link #toString()} is overridden so that logging
 * this object by mistake prints nothing useful.
 */
public record Secrets(String temporaryPassword, String resetToken, String resetOtp) {

    public static final Secrets NONE = new Secrets(null, null, null);

    public Secrets(String temporaryPassword, String resetToken) {
        this(temporaryPassword, resetToken, null);
    }

    public static Secrets from(Map<String, String> metadata) {
        if (metadata == null) {
            return NONE;
        }
        return new Secrets(blankToNull(metadata.get("temporaryPassword")),
                blankToNull(metadata.get("resetToken")), blankToNull(metadata.get("resetOtp")));
    }

    public boolean isEmpty() {
        return temporaryPassword == null && resetToken == null && resetOtp == null;
    }

    @Override
    public String toString() {
        return isEmpty() ? "Secrets[none]" : "Secrets[redacted]";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
