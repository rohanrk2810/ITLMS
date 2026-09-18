package com.itilms.certificate.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * The code printed beside a certificate number, needed to verify it publicly.
 *
 * <p>Ten characters from an alphabet without the ones people misread on paper
 * - no I, L, O, 0 or 1 - printed as two groups of five. About 2^49 possible
 * codes: with the verification endpoint rate-limited, guessing one for a given
 * certificate number is not a practical way to learn a graduate's name.
 */
public final class VerificationCodes {

    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private VerificationCodes() {
    }

    /** e.g. {@code K7QMX-2HWRP} */
    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH + 1);
        for (int i = 0; i < LENGTH; i++) {
            if (i == LENGTH / 2) {
                code.append('-');
            }
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    /**
     * Whether a typed code matches the stored one.
     *
     * <p>Forgiving about how it was typed - case, spaces, the dash - since it
     * is copied by hand from paper. The comparison takes the same time whether
     * the first character or the last is wrong, so response timing does not
     * reveal how close a guess was.
     */
    public static boolean matches(String stored, String supplied) {
        if (stored == null || supplied == null) {
            return false;
        }
        byte[] expected = normalise(stored).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = normalise(supplied).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    static String normalise(String code) {
        return code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
