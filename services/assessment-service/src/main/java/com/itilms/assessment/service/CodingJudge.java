package com.itilms.assessment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Component;

import com.itilms.assessment.client.CodeClient;
import com.itilms.assessment.entity.QuizQuestion;
import com.itilms.assessment.entity.QuizTestCase;
import com.itilms.common.exception.BusinessRuleException;

import lombok.RequiredArgsConstructor;

/**
 * Marks a coding answer: runs the program against every test case and compares what it printed.
 *
 * <p>The comparison forgives what a student cannot see: line endings, trailing spaces on a line and blank
 * lines at the end. It does not forgive anything else - a different number, a missing word or a different
 * order is a failed case.
 */
@Component
@RequiredArgsConstructor
public class CodingJudge {

    private static final String COMPILE_ERROR = "COMPILE_ERROR";
    private static final String SUCCESS = "SUCCESS";
    private static final String TIME_LIMIT = "TIME_LIMIT_EXCEEDED";

    private final CodeClient codeClient;

    /** One test case's outcome. {@code input}, {@code expected} and {@code actual} are for the caller to hide or show. */
    public record CaseResult(int number, boolean hidden, boolean passed, int weight,
                             String input, String expected, String actual, String error,
                             Double timeSeconds, Integer memoryKb) {

        /** A case with no measurements (the runner reported none). */
        public CaseResult(int number, boolean hidden, boolean passed, int weight,
                          String input, String expected, String actual, String error) {
            this(number, hidden, passed, weight, input, expected, actual, error, null, null);
        }
    }

    /** The whole run. {@code compileError} is set when the program did not compile; every case then failed. */
    public record Verdict(String compileError, List<CaseResult> cases) {

        public int passedCount() {
            return (int) cases.stream().filter(CaseResult::passed).count();
        }

        public int passedWeight() {
            return cases.stream().filter(CaseResult::passed).mapToInt(CaseResult::weight).sum();
        }
    }

    /**
     * @throws BusinessRuleException when the question has no test cases, or the runner could not be used
     *                               (busy, over the limit, down): the answer is then not graded, not failed
     */
    public Verdict judge(QuizQuestion question, String sourceCode) {
        List<QuizTestCase> cases = question.getTestCases();
        if (cases.isEmpty() || question.getCodeLanguage() == null) {
            throw new BusinessRuleException("This question has no test cases to run.");
        }

        List<CodeClient.RunResult> results = codeClient.runBatch(new CodeClient.RunBatchRequest(
                question.getCodeLanguage().name(), sourceCode, cases.stream().map(QuizTestCase::getInput).toList()));
        if (results == null || results.size() != cases.size()) {
            throw new BusinessRuleException("CODE_RUNNER_UNAVAILABLE", "The code runner gave an incomplete answer. Try again.");
        }

        String compileError = null;
        List<CaseResult> outcomes = new ArrayList<>(cases.size());
        for (int i = 0; i < cases.size(); i++) {
            QuizTestCase testCase = cases.get(i);
            CodeClient.RunResult run = results.get(i);

            boolean passed = false;
            String error = null;
            if (COMPILE_ERROR.equals(run.outcome())) {
                compileError = firstNonBlank(run.compileOutput(), run.stderr(), run.statusDescription());
                error = "Did not compile";
            } else if (TIME_LIMIT.equals(run.outcome())) {
                error = "Took too long and was stopped";
            } else if (!SUCCESS.equals(run.outcome())) {
                error = "Crashed while running" + (isBlank(run.stderr()) ? "" : ": " + run.stderr().strip());
            } else {
                passed = outputsMatch(run.stdout(), testCase.getExpectedOutput());
            }
            outcomes.add(new CaseResult(i + 1, testCase.isHidden(), passed, testCase.getWeight(),
                    testCase.getInput(), testCase.getExpectedOutput(), run.stdout(), error,
                    run.timeSeconds(), run.memoryKb()));
        }
        return new Verdict(compileError, outcomes);
    }

    /** Same text once line endings, trailing spaces on each line and trailing blank lines are ignored. */
    public static boolean outputsMatch(String actual, String expected) {
        return canonical(actual).equals(canonical(expected));
    }

    private static String canonical(String text) {
        if (text == null) {
            return "";
        }
        List<String> lines = new ArrayList<>(List.of(text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)));
        lines.replaceAll(String::stripTrailing);
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return String.join("\n", lines);
    }

    /** Identifies the exact code a verdict belongs to. */
    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (!isBlank(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
