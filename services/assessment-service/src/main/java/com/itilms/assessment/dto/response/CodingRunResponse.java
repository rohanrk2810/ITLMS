package com.itilms.assessment.dto.response;

import java.util.List;

import com.itilms.assessment.service.CodingJudge;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a student is told after running a coding question's tests.
 *
 * <p>A visible case shows its input, the expected output and what the program printed. A hidden case shows
 * only whether it passed: its input, its expected output and the program's output are all left out, so the
 * hidden tests cannot be read back out of the answers to them.
 */
@Schema(description = "Result of running a coding question's test cases")
public record CodingRunResponse(
        Long questionId,
        int passed,
        int total,
        @Schema(description = "The compiler's messages when the program did not compile; otherwise null")
        String compileError,
        List<Case> cases
) {

    public record Case(int number, boolean hidden, boolean passed, String input, String expectedOutput,
                       String actualOutput, String error) {
    }

    public static CodingRunResponse of(Long questionId, CodingJudge.Verdict verdict) {
        List<Case> cases = verdict.cases().stream().map(c -> c.hidden()
                ? new Case(c.number(), true, c.passed(), null, null, null,
                        c.error() == null ? null : "Did not finish")
                : new Case(c.number(), false, c.passed(), c.input(), c.expected(), c.actual(), c.error())).toList();
        return new CodingRunResponse(questionId, verdict.passedCount(), verdict.cases().size(),
                verdict.compileError(), cases);
    }
}
