package com.itilms.common.util;

import java.security.SecureRandom;
import java.time.Year;
import java.util.Base64;

/**
 * Human-facing identifiers: student codes, batch codes, receipt and certificate
 * numbers, and the non-guessable handles used for file downloads.
 *
 * <p>Two different jobs here, and they must not be confused:
 * <ul>
 *   <li><b>Readable codes</b> ({@code STU-2026-000123}) are sequential and
 *       predictable on purpose — staff read them over the phone. They are
 *       identifiers, never secrets, and never the only thing protecting a
 *       record.</li>
 *   <li><b>Opaque handles</b> ({@link #secureRef()}) are random and must stay
 *       unguessable, because Doc S17 requires certificate and receipt URLs to be
 *       non-guessable. Authorization is still checked on top; the random handle
 *       is defence in depth, not the defence.</li>
 * </ul>
 */
public final class Codes {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Codes() {
    }

    /** {@code STU-2026-000042} */
    public static String studentCode(long sequence) {
        return "STU-%d-%06d".formatted(Year.now().getValue(), sequence);
    }

    /** {@code TRN-2026-0007} */
    public static String trainerCode(long sequence) {
        return "TRN-%d-%04d".formatted(Year.now().getValue(), sequence);
    }

    /**
     * {@code JFS-2026-B03} — course code, year and a per-course batch number, so
     * a coordinator can tell what a batch teaches without opening it.
     */
    public static String batchCode(String courseCode, long sequenceWithinCourse) {
        return "%s-%d-B%02d".formatted(courseCode.toUpperCase(), Year.now().getValue(), sequenceWithinCourse);
    }

    /** {@code RCPT-2026-000015} */
    public static String receiptNo(long sequence) {
        return "RCPT-%d-%06d".formatted(Year.now().getValue(), sequence);
    }

    /**
     * {@code ITILMS-2026-0000123} — printed on the certificate and typed into
     * the public verification page.
     */
    public static String certificateNo(long sequence) {
        return "ITILMS-%d-%07d".formatted(Year.now().getValue(), sequence);
    }

    /** 160 bits of randomness, URL-safe. Used for file handles and token ids. */
    public static String secureRef() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    /** A temporary password for a freshly created account; the user must change it. */
    public static String temporaryPassword() {
        byte[] bytes = new byte[9];
        RANDOM.nextBytes(bytes);
        return "Itilms@" + URL_ENCODER.encodeToString(bytes);
    }

    /**
     * Strips anything that could escape a directory or confuse a shell, leaving a
     * name that is still recognisable to the person who uploaded it
     * (Doc S17: "generate safe server-side file names").
     */
    public static String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "file";
        }
        String base = originalName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        while (base.startsWith(".")) {
            base = base.substring(1);
        }
        if (base.isBlank()) {
            base = "file";
        }
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}
