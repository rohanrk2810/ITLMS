package com.itilms.batch.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The outcome of a bulk enrolment.
 *
 * <p>Reports each student separately instead of failing the whole request on the
 * first problem. Enrolling a class of twenty and being told "three were already
 * enrolled, the other seventeen are in" is actionable; a single rejection means
 * the coordinator has to work out which ones by hand.
 */
@Schema(description = "Result of enrolling several students")
public record EnrollmentResultResponse(
        Long batchId,
        int enrolled,
        int skipped,
        long seatsRemaining,
        List<Outcome> outcomes
) {

    @Schema(description = "What happened to one student")
    public record Outcome(Long studentId, boolean success, String message) {

        public static Outcome ok(Long studentId) {
            return new Outcome(studentId, true, "Enrolled");
        }

        public static Outcome failed(Long studentId, String reason) {
            return new Outcome(studentId, false, reason);
        }
    }
}
