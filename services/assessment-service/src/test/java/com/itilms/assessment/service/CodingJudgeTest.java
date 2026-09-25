package com.itilms.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.itilms.assessment.client.CodeClient;
import com.itilms.assessment.client.CodeClient.RunResult;
import com.itilms.assessment.entity.QuestionType;
import com.itilms.assessment.entity.QuizAnswer;
import com.itilms.assessment.entity.QuizQuestion;
import com.itilms.assessment.entity.QuizTestCase;
import com.itilms.common.code.CodeLanguage;
import com.itilms.common.exception.BusinessRuleException;

/** How a coding answer is marked: what counts as the same output, and how marks follow the passing weight. */
class CodingJudgeTest {

    private CodeClient client;
    private CodingJudge judge;
    private QuizQuestion question;

    @BeforeEach
    void setUp() {
        client = mock(CodeClient.class);
        judge = new CodingJudge(client);
        question = QuizQuestion.builder().id(1L).type(QuestionType.CODING).codeLanguage(CodeLanguage.PYTHON).marks(10).build();
        question.getTestCases().add(QuizTestCase.builder().sequenceNo(1).input("1 2").expectedOutput("3").weight(1).build());
        question.getTestCases().add(QuizTestCase.builder().sequenceNo(2).input("5 5").expectedOutput("10").weight(3).hidden(true).build());
    }

    private static RunResult ok(String stdout) {
        return new RunResult("PYTHON", "SUCCESS", "Accepted", stdout, null, null, null, 0.1, 100, false);
    }

    private static RunResult outcome(String outcome, String stderr, String compileOutput) {
        return new RunResult("PYTHON", outcome, outcome, null, stderr, compileOutput, null, null, null, false);
    }

    @Test
    @DisplayName("Line endings, trailing spaces and trailing blank lines do not matter; anything else does")
    void outputComparison() {
        assertThat(CodingJudge.outputsMatch("3\n", "3")).isTrue();
        assertThat(CodingJudge.outputsMatch("3  \r\n\r\n", "3")).isTrue();
        assertThat(CodingJudge.outputsMatch("a \nb\n\n\n", "a\nb")).isTrue();
        assertThat(CodingJudge.outputsMatch(null, "")).isTrue();

        assertThat(CodingJudge.outputsMatch("3.0", "3")).isFalse();
        assertThat(CodingJudge.outputsMatch("b\na", "a\nb")).isFalse();
        assertThat(CodingJudge.outputsMatch("Hello", "hello")).isFalse();
        assertThat(CodingJudge.outputsMatch(" 3", "3")).isFalse();   // leading spaces are the program's own doing
    }

    @Test
    @DisplayName("Each case is passed or failed on its own, and the marks follow the weight that passed")
    void partialCredit() {
        when(client.runBatch(any())).thenReturn(List.of(ok("3\n"), ok("11\n")));

        CodingJudge.Verdict verdict = judge.judge(question, "code");

        assertThat(verdict.cases()).extracting(CodingJudge.CaseResult::passed).containsExactly(true, false);
        assertThat(verdict.passedCount()).isEqualTo(1);
        assertThat(verdict.passedWeight()).isEqualTo(1);
        assertThat(question.codingMarks(verdict.passedWeight())).isEqualTo(3);   // 1 of 4 weight, of 10 marks -> 2.5 rounds to 3
        assertThat(question.codingMarks(4)).isEqualTo(10);
        assertThat(question.codingMarks(0)).isZero();
    }

    @Test
    @DisplayName("The runner is sent the language, the code and every input in order")
    void sendsInputsInOrder() {
        when(client.runBatch(any())).thenReturn(List.of(ok("3"), ok("10")));

        judge.judge(question, "print(1)");

        verify(client).runBatch(new CodeClient.RunBatchRequest("PYTHON", "print(1)", List.of("1 2", "5 5")));
    }

    @Test
    @DisplayName("A program that does not compile fails every case and reports the compiler's message")
    void compileError() {
        RunResult broken = outcome("COMPILE_ERROR", null, "Main.java:1: error: ';' expected");
        when(client.runBatch(any())).thenReturn(List.of(broken, broken));

        CodingJudge.Verdict verdict = judge.judge(question, "class");

        assertThat(verdict.compileError()).contains("expected");
        assertThat(verdict.passedCount()).isZero();
        assertThat(verdict.cases()).allSatisfy(c -> assertThat(c.error()).isEqualTo("Did not compile"));
    }

    @Test
    @DisplayName("A crash or a timeout fails that case even if it printed the right thing first")
    void crashAndTimeout() {
        when(client.runBatch(any())).thenReturn(List.of(
                new RunResult("PYTHON", "RUNTIME_ERROR", "Runtime Error", "3\n", "ZeroDivisionError", null, null, null, null, false),
                outcome("TIME_LIMIT_EXCEEDED", null, null)));

        CodingJudge.Verdict verdict = judge.judge(question, "code");

        assertThat(verdict.passedCount()).isZero();
        assertThat(verdict.cases().get(0).error()).contains("Crashed").contains("ZeroDivisionError");
        assertThat(verdict.cases().get(1).error()).contains("too long");
    }

    @Test
    @DisplayName("A question with no test cases cannot be judged, and nothing is run")
    void noTestCases() {
        question.getTestCases().clear();

        assertThatThrownBy(() -> judge.judge(question, "code")).isInstanceOf(BusinessRuleException.class);
        verify(client, never()).runBatch(any());
    }

    @Test
    @DisplayName("A runner that answers with the wrong number of results is refused, not guessed at")
    void incompleteAnswer() {
        when(client.runBatch(any())).thenReturn(List.of(ok("3")));

        assertThatThrownBy(() -> judge.judge(question, "code")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("A short answer matches an accepted answer ignoring case and spacing, and nothing else")
    void shortAnswerMatching() {
        QuizQuestion q = QuizQuestion.builder().id(2L).type(QuestionType.SHORT_ANSWER).marks(2).build();
        q.getAcceptedAnswers().addAll(List.of("New Delhi", "Delhi"));

        assertThat(q.scoreText("  new   delhi ")).isEqualTo(2);
        assertThat(q.scoreText("DELHI")).isEqualTo(2);
        assertThat(q.scoreText("Delhi, India")).isZero();
        assertThat(q.scoreText("  ")).isZero();
        assertThat(q.scoreText(null)).isZero();
    }

    @Test
    @DisplayName("Scoring reads the stored verdict for code and the typed text for short answers; it never runs anything")
    void scorerMarksByType() {
        QuizAnswer tested = QuizAnswer.builder().answerText("code").testedMarks(7).build();
        QuizAnswer untested = QuizAnswer.builder().answerText("code").build();
        assertThat(AttemptScorer.marksFor(question, tested)).isEqualTo(7);
        assertThat(AttemptScorer.marksFor(question, untested)).isZero();

        QuizQuestion sa = QuizQuestion.builder().id(2L).type(QuestionType.SHORT_ANSWER).marks(2).build();
        sa.getAcceptedAnswers().add("yes");
        assertThat(AttemptScorer.marksFor(sa, QuizAnswer.builder().answerText("YES").build())).isEqualTo(2);
        assertThat(AttemptScorer.marksFor(sa, QuizAnswer.builder().answerText("no").build())).isZero();
    }
}
