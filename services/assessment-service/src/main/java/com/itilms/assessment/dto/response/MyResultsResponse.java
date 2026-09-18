package com.itilms.assessment.dto.response;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Everything a student has been assessed on, in one place (Doc S8.2 "Results"). */
@Schema(description = "The signed-in student's results")
public record MyResultsResponse(
        List<TestResult> tests,
        List<AssignmentResult> assignments
) {

    @Schema(description = "A test: the best of the student's attempts")
    public record TestResult(
            Long quizId,
            String title,
            Long courseId,
            int attempts,
            @Schema(description = "Null while the trainer is holding results back")
            Integer bestPercentage,
            Boolean passed,
            int passPercentage,
            boolean mandatory
    ) {
    }

    @Schema(description = "An assignment the student has handed in")
    public record AssignmentResult(
            Long assignmentId,
            Long submissionId,
            String title,
            String status,
            Integer marks,
            int maxMarks,
            String feedback,
            Instant submittedAt,
            Instant evaluatedAt
    ) {
    }
}
