package com.itilms.certificate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * The completion rule (Doc S7.3) and what goes on the certificate.
 *
 * <p>Each criterion can be turned off, because institutes differ - one that
 * does not track attendance for weekend batches should not have every weekend
 * student refused a certificate for it. Turning one off is a configuration
 * change, visible and deliberate, never a code path someone forgot.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "itilms.certificate")
public class CertificateProperties {

    private Criteria criteria = new Criteria();

    /**
     * Whether TRAINERs may see certificate requests (read only; deciding is always ADMIN only).
     * Off unless an administrator turns it on.
     */
    private boolean trainerAccess = false;

    /** Where the printed QR code and link point, e.g. https://institute.example/verify. */
    private String verificationBaseUrl = "http://localhost:5173/verify";

    private String instituteName = "IT Training Institute";

    private String signatoryName = "Director";

    /** Used when naming an outstanding fee as the reason a certificate is withheld. */
    private String currencySymbol = "Rs.";

    /** The date printed as "issued on" is the institute's date, not the server's. */
    private java.time.ZoneId zone = java.time.ZoneId.of("Asia/Kolkata");

    @Getter
    @Setter
    public static class Criteria {
        private boolean requireAllMandatoryLessons = true;
        private boolean requireAllQuizzesPassed = true;
        /** Handed in and marked. */
        private boolean requireAllAssignmentsComplete = true;
        private boolean requireAttendance = true;
        private int minimumAttendancePercent = 75;
        /** Applied only when the course has mandatory tests; 0 turns it off. */
        private int minimumAverageScorePercent = 40;
        private boolean requireFeesCleared = true;
    }
}
