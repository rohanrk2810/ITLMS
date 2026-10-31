package com.itilms.assessment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What a student may now attempt on a test")
public record ExtraAttemptResponse(
        Long studentId,
        @Schema(description = "Attempts given to this student on top of the test's own")
        int extraAttempts,
        @Schema(description = "The test's attempts plus the extra ones: how many this student may make in all")
        int attemptsAllowed
) {
}
