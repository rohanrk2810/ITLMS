package com.itilms.assessment.dto.request;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "An MCQ test (Doc S6.11)")
public record CreateQuizRequest(

        @NotNull(message = "Course is required")
        Long courseId,

        @Schema(description = "Leave blank to open the test to every batch studying the course")
        Long batchId,

        @NotBlank(message = "Title is required")
        @Size(max = 200)
        String title,

        String instructions,

        @NotNull(message = "Duration is required")
        @Min(value = 1, message = "Duration must be at least a minute")
        @Max(value = 600, message = "Duration cannot exceed 10 hours")
        Integer durationMinutes,

        @Min(value = 0) @Max(value = 100)
        Integer passPercentage,

        @Min(value = 1, message = "At least one attempt must be allowed")
        @Max(value = 20)
        Integer attemptsAllowed,

        @Schema(description = "When the test opens. Blank means as soon as it is published.")
        Instant availableFrom,

        @Schema(description = "When the test closes. Blank means it stays open.")
        Instant availableUntil,

        Boolean shuffleQuestions,

        @Schema(description = "Show the score the moment the student submits. Turn off for a "
                + "test whose questions will be reused with a later batch.")
        Boolean showResultImmediately,

        @Schema(description = "Counts toward course completion. Default true.")
        Boolean mandatory
) {
}
