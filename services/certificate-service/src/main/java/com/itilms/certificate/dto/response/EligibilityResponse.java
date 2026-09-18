package com.itilms.certificate.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whether a student may be given a certificate, criterion by criterion.
 *
 * <p>Shown to the student as a checklist, so "not eligible" always comes with
 * what to do about it.
 */
@Schema(description = "Certificate eligibility (Doc S7.3)")
public record EligibilityResponse(
        Long studentId,
        Long courseId,
        Long batchId,
        boolean eligible,
        List<Criterion> criteria,
        @Schema(description = "Tests and assignments still outstanding, by title")
        List<String> outstandingWork,
        @Schema(description = "Set when a valid certificate has already been issued")
        String existingCertificateNo
) {

    public enum Outcome {
        MET,
        NOT_MET,
        /** The owning service could not be asked. Never treated as met. */
        UNAVAILABLE,
        /** Turned off in this institute's configuration. */
        NOT_REQUIRED
    }

    @Schema(description = "One condition of the completion rule")
    public record Criterion(String key, String name, Outcome outcome, String detail) {

        public static Criterion met(String key, String name, String detail) {
            return new Criterion(key, name, Outcome.MET, detail);
        }

        public static Criterion notMet(String key, String name, String detail) {
            return new Criterion(key, name, Outcome.NOT_MET, detail);
        }

        public static Criterion unavailable(String key, String name, String detail) {
            return new Criterion(key, name, Outcome.UNAVAILABLE, detail);
        }

        public static Criterion notRequired(String key, String name) {
            return new Criterion(key, name, Outcome.NOT_REQUIRED, null);
        }
    }
}
