package com.itilms.assessment.dto.response;

import java.time.Instant;

import com.itilms.assessment.entity.Quiz;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A test as it appears on a student's list: no questions, just whether they can
 * sit it and how they did.
 */
@Schema(description = "A test available to the signed-in student")
public record StudentQuizResponse(
        Long id,
        Long courseId,
        Long batchId,
        String title,
        String instructions,
        int durationMinutes,
        int totalMarks,
        int passPercentage,
        int attemptsAllowed,
        int attemptsUsed,
        Instant availableFrom,
        Instant availableUntil,
        boolean mandatory,

        @Schema(description = "True when the test is open right now")
        boolean openNow,

        @Schema(description = "True when the student may start a new attempt")
        boolean canStart,

        @Schema(description = "The attempt to resume, when one is still running")
        Long inProgressAttemptId,

        @Schema(description = "Best result so far; null until results are released")
        Integer bestPercentage,

        Boolean passed
) {

    public static StudentQuizResponse of(Quiz q, int attemptsUsed, Long inProgressAttemptId,
                                         Integer bestPercentage, boolean resultVisible, Instant now) {
        boolean open = q.isOpenAt(now);
        boolean canStart = open && inProgressAttemptId == null && attemptsUsed < q.getAttemptsAllowed();
        Integer best = resultVisible ? bestPercentage : null;
        return new StudentQuizResponse(
                q.getId(), q.getCourseId(), q.getBatchId(), q.getTitle(), q.getInstructions(),
                q.getDurationMinutes(), q.getTotalMarks(), q.getPassPercentage(), q.getAttemptsAllowed(),
                attemptsUsed, q.getAvailableFrom(), q.getAvailableUntil(), q.isMandatory(),
                open, canStart, inProgressAttemptId, best,
                best == null ? null : q.passed(best));
    }
}
