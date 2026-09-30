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
