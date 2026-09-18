package com.itilms.assessment.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whether a student has done the assessed work a course requires (Doc S7.3).
 *
 * <p>certificate-service asks this before issuing a certificate. The answer
 * names what is still outstanding, so the certificate screen can tell a
 * student what is missing rather than just "not eligible".
 */
@Schema(description = "Assessment requirements for completing a course")
public record CompletionResponse(
        Long studentId,
        Long courseId,
        Long batchId,
        int testsRequired,
        int testsPassed,
        int assignmentsRequired,
        int assignmentsEvaluated,
        @Schema(description = "True when every mandatory test is passed and every mandatory assignment marked")
        boolean complete,
        @Schema(description = "Titles of the work still outstanding")
        List<String> outstanding
) {
}
