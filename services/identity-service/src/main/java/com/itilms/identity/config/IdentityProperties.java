package com.itilms.identity.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** Binds {@code itilms.bootstrap.*} and {@code itilms.security.*}. */
@Getter
@Setter
@ConfigurationProperties(prefix = "itilms")
public class IdentityProperties {

    private Bootstrap bootstrap = new Bootstrap();
    private Security security = new Security();
    private Captcha captcha = new Captcha();

    /**
     * The "verify you are human" check (Cloudflare Turnstile) on sign-in, registration and forgot-password.
     *
     * <p>Off by default, so a plain local run works with no keys. When on, the secret is required: the service
     * refuses to start rather than quietly run unprotected.
     */
    @Getter
    @Setter
    public static class Captcha {
        private boolean enabled = false;
        /** Public. Handed to the browser so it can show the widget. */
        private String siteKey = "";
        /** Secret. Only this service ever sees it. */
        private String secret = "";
        private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
        private Duration timeout = Duration.ofSeconds(5);
        /**
         * Secret shared with the SutraVidya desktop app. The app sends it in the X-Desktop-Key header and the check is
         * skipped for those requests: Cloudflare's widget does not pass inside an embedded browser. Blank (the default)
         * means no such exemption. It is a shared secret inside a distributed app, so it keeps honest clients working
         * rather than stopping someone who unpacks the app; the gateway's rate limit and the sign-in lock-out still apply.
         */
        private String desktopKey = "";
    }

    /**
     * The first administrator.
     *
     * <p>A brand-new database has no accounts, so nobody can sign in to create
     * the first one. This seeds exactly one ADMIN on first start and then does
     * nothing on every subsequent start.
     */
    @Getter
    @Setter
    public static class Bootstrap {
        private boolean enabled = true;
        private String adminEmail = "admin@itinstitute.local";
        /** No default. Startup fails loudly rather than shipping a known password. */
        private String adminPassword;
        private String adminFirstName = "Institute";
        private String adminLastName = "Administrator";
    }

    @Getter
    @Setter
    public static class Security {

        /**
         * Whether anyone can create their own student account.
         *
         * <p>Off by default. The document allows self-registration only "where
         * enabled" (S11), and its admission workflow (S7.1) has the institute
         * create the account once an admission is confirmed. Left open, the
         * public sign-up form becomes a way to fill the student register with
         * people who never enquired, never paid, and cannot be told apart from
         * real admissions.
         */
        private boolean selfRegistrationEnabled = false;

        private int maxFailedAttempts = 5;
        private Duration lockoutDuration = Duration.ofMinutes(15);
        /** Cap on password-reset emails per account per hour. */
        private int maxResetRequestsPerHour = 3;
        private Password password = new Password();
        private Sessions sessions = new Sessions();

        /**
         * One active login per user. {@code singleSessionRoles} lists the roles the rule
         * applies to (all roles by default; an empty list switches it off).
         */
        @Getter
        @Setter
        public static class Sessions {
            private java.util.List<String> singleSessionRoles = java.util.List.of(
                    "ADMIN", "COORDINATOR", "TRAINER", "STUDENT", "FINANCE", "PLACEMENT");
            /** REPLACE: a new login ends the old session. DENY: the new login is refused. */
            private ConflictPolicy conflictPolicy = ConflictPolicy.REPLACE;
            /** Under DENY, a session idle longer than this no longer blocks a new login. */
            private Duration idleTimeout = Duration.ofMinutes(30);

            public boolean appliesTo(String role) {
                return singleSessionRoles != null && singleSessionRoles.contains(role);
            }
        }

        public enum ConflictPolicy { REPLACE, DENY }

        @Getter
        @Setter
        public static class Password {
            private int minLength = 8;
            private boolean requireDigit = true;
            private boolean requireUpper = true;
            private boolean requireLower = true;
            private boolean requireSymbol = false;
        }
    }
}
