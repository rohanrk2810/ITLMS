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
        List<Case> cases,
        @Schema(description = "How this run compares with other students; only when every test passed")
        Comparison comparison
) {

    /**
     * Faster-than figures against the other students who solved this question. When fewer than {@code minimumSample}
     * have, {@code available} is false and nothing else is filled in: a percentage over a handful means nothing.
     */
    public record Comparison(boolean available, int sampleSize, int minimumSample, Integer runtimeBeatsPercent,
                             Integer memoryBeatsPercent, int yourRuntimeMs, Integer yourMemoryKb,
                             List<Bucket> runtimeBuckets, List<Bucket> memoryBuckets) {
    }

    /** One column of a histogram: how many students fell in from..to, and whether this student is one of them. */
    public record Bucket(int from, int to, int count, boolean yours) {
    }

    public CodingRunResponse withComparison(Comparison comparison) {
        return new CodingRunResponse(questionId, passed, total, compileError, cases, comparison);
    }


    /** Time and memory are measurements, not answers, so a hidden case shows them too. */
    public record Case(int number, boolean hidden, boolean passed, String input, String expectedOutput,
                       String actualOutput, String error, Double timeSeconds, Integer memoryKb) {
    }

    public static CodingRunResponse of(Long questionId, CodingJudge.Verdict verdict) {
        List<Case> cases = verdict.cases().stream().map(c -> c.hidden()
                ? new Case(c.number(), true, c.passed(), null, null, null,
                        c.error() == null ? null : "Did not finish", c.timeSeconds(), c.memoryKb())
                : new Case(c.number(), false, c.passed(), c.input(), c.expected(), c.actual(), c.error(),
                        c.timeSeconds(), c.memoryKb())).toList();
        return new CodingRunResponse(questionId, verdict.passedCount(), verdict.cases().size(),
                verdict.compileError(), cases, null);
    }
}
