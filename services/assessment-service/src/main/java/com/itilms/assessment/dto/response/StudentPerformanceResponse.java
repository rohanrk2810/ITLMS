package com.itilms.assessment.dto.response;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How one student is getting on with assessed work: tests, coding questions and assignments. Feeds the student
 * progress report; the report's suggestions are worked out from the lists here (what is pending, what is weak).
 */
@Schema(description = "A student's performance on tests, coding questions and assignments")
public record StudentPerformanceResponse(Tests tests, Coding coding, Assignments assignments) {

    @Schema(description = "Something the student still has to do")
    public record Pending(Long id, String title, Instant dueAt, boolean overdue) {
    }

    @Schema(description = "A test or assignment that went badly")
    public record Weak(Long id, String title, int percent, int passPercent) {
    }

    @Schema(description = "Tests (choice, short answer and coding questions alike)")
    public record Tests(
            @Schema(description = "Tests with at least one finished attempt")
            int attempted,
            int passed,
            @Schema(description = "Mean of the best result on each test whose result is visible; null when there is none")
            Integer averagePercent,
            Integer bestPercent,
            @Schema(description = "Attempts that were ended by the secure-test rules")
            int terminated,
            @Schema(description = "Tests taken whose result a trainer is still holding back (student view only)")
            int resultsPending,
            @Schema(description = "Open tests for the student's batches that they have not taken")
            List<Pending> pending,
            @Schema(description = "Tests below the pass mark, worst first")
            List<Weak> below) {
    }

    @Schema(description = "Coding questions inside tests")
    public record CodingWeak(Long questionId, String question, int passed, int total) {
    }

    public record Coding(
            int questionsAttempted,
            @Schema(description = "Test cases passed, on the best attempt at each question")
            int testCasesPassed,
            int testCasesTotal,
            @Schema(description = "Mean, over the questions attempted, of the share of test cases passed; null when none")
            Integer averagePercent,
            @Schema(description = "Questions where not every test case passed, worst first")
            List<CodingWeak> lowest) {
    }

    public record Assignments(
            @Schema(description = "Published or closed assignments set for the student's batches")
            int assigned,
            int submitted,
            int evaluated,
            @Schema(description = "Mean of marks as a percentage of the maximum, over evaluated work; null when none")
            Integer averagePercent,
            @Schema(description = "Sent back for rework and not yet handed in again")
            int returned,
            @Schema(description = "Handed in after the deadline")
            int late,
            List<Pending> pending) {
    }
}
