package com.itilms.assessment.dto.response;

import java.time.Instant;

import com.itilms.assessment.entity.QuizViolation;

import io.swagger.v3.oas.annotations.media.Schema;

/** One recorded event (for the trainer's review), and what a report of one did (for the student's page). */
public final class ViolationResponse {

    private ViolationResponse() {
    }

    @Schema(description = "A recorded event, for staff")
    public record Entry(Long id, String type, boolean counted, String detail, Instant occurredAt, Instant clientAt) {

        public static Entry from(QuizViolation v) {
            return new Entry(v.getId(), v.getType().name(), v.isCounted(), v.getDetail(), v.getOccurredAt(), v.getClientAt());
        }
    }

    @Schema(description = "What reporting an event did to the attempt")
    public record Outcome(
            boolean counted,
            int violationCount,
            int maxViolations,
            @Schema(description = "How many more counted violations the student may have before the attempt ends")
            int warningsLeft,
            @Schema(description = "True when this report ended the attempt")
            boolean terminated,
            String message) {
    }
}
