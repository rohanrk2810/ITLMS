package com.itilms.assessment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.itilms.assessment.dto.request.SubmitAttemptRequest;
import com.itilms.assessment.dto.request.ViolationRequest;
import com.itilms.assessment.entity.AttemptStatus;
import com.itilms.assessment.entity.QuestionType;
import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizAnswer;
import com.itilms.assessment.entity.QuizAttempt;
import com.itilms.assessment.entity.QuizOption;
import com.itilms.assessment.entity.QuizQuestion;
import com.itilms.assessment.entity.QuizStatus;
import com.itilms.assessment.entity.QuizTestCase;
import com.itilms.assessment.entity.QuizViolation;
import com.itilms.assessment.entity.ViolationType;
import com.itilms.assessment.repository.QuizAnswerRepository;
import com.itilms.assessment.repository.QuizAttemptRepository;
import com.itilms.assessment.repository.QuizQuestionRepository;
import com.itilms.assessment.repository.QuizRepository;
import com.itilms.assessment.repository.QuizViolationRepository;
import com.itilms.assessment.service.AssessmentAccess;
import com.itilms.assessment.service.AttemptScorer;
import com.itilms.assessment.service.CodingJudge;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;

/**
 * The ways a student could try to get a mark they did not earn, and the ways
 * the clock ends a sitting.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttemptServiceImplTest {

    private static final long STUDENT = 3L;

    @Mock private QuizRepository quizRepository;
    @Mock private QuizQuestionRepository questionRepository;
    @Mock private QuizAttemptRepository attemptRepository;
    @Mock private QuizAnswerRepository answerRepository;
    @Mock private AssessmentAccess access;
    @Mock private AttemptScorer scorer;
    @Mock private CodingJudge judge;
    @Mock private QuizViolationRepository violations;

    private AttemptServiceImpl service;
    private Quiz quiz;
    private QuizQuestion q1;
    private QuizQuestion q2;

    @BeforeEach
    void setUp() {
        service = new AttemptServiceImpl(quizRepository, questionRepository, attemptRepository,
                answerRepository, access, scorer, judge, violations);

        quiz = Quiz.builder().id(1L).courseId(1L).title("t").durationMinutes(30).totalMarks(2)
                .attemptsAllowed(1).status(QuizStatus.PUBLISHED).build();
        q1 = question(10L, 100L, 101L);
        q2 = question(20L, 200L, 201L);

        when(access.requireStudent()).thenReturn(new AppPrincipal(30L, "s@x", "S", "STUDENT", STUDENT));
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(questionRepository.findWithOptions(1L)).thenReturn(List.of(q1, q2));
        when(answerRepository.findByAttemptId(any())).thenReturn(List.of());
    }

    private static QuizQuestion question(long id, long rightOption, long wrongOption) {
        QuizQuestion q = QuizQuestion.builder().id(id).quizId(1L).type(QuestionType.SINGLE_CHOICE)
                .marks(1).sequenceNo((int) id).questionText("q" + id).build();
        q.addOption(QuizOption.builder().id(rightOption).optionText("right").correct(true).sequenceNo(1).build());
        q.addOption(QuizOption.builder().id(wrongOption).optionText("wrong").correct(false).sequenceNo(2).build());
        return q;
    }

    private QuizAttempt attempt(long studentId, Instant expiresAt) {
        QuizAttempt a = QuizAttempt.builder().id(9L).quizId(1L).studentId(studentId).attemptNo(1)
                .startedAt(expiresAt.minusSeconds(1800)).expiresAt(expiresAt)
                .status(AttemptStatus.IN_PROGRESS).build();
        when(attemptRepository.findById(9L)).thenReturn(Optional.of(a));
        return a;
    }

    private static SubmitAttemptRequest answers(long questionId, Long... options) {
        return new SubmitAttemptRequest(List.of(new SubmitAttemptRequest.Answer(questionId, Set.of(options), null)));
    }

    @Test
    @DisplayName("An option taken from another question is refused, not scored")
    void foreignOptionRefused() {
        attempt(STUDENT, Instant.now().plusSeconds(600));

        // The right answer to question 20, submitted against question 10.
        assertThatThrownBy(() -> service.saveAnswers(9L, answers(10L, 200L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not belong");
        verify(answerRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("A question id from a different test is refused")
    void foreignQuestionRefused() {
        attempt(STUDENT, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> service.saveAnswers(9L, answers(999L, 100L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not part of this test");
    }

    @Test
    @DisplayName("Two answers to a single-choice question are refused")
    void hedgingRefused() {
        attempt(STUDENT, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> service.saveAnswers(9L, answers(10L, 100L, 101L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("only one answer");
    }

    @Test
    @DisplayName("Another student's attempt looks like it does not exist")
    void othersAttemptHidden() {
        attempt(99L, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> service.saveAnswers(9L, answers(10L, 100L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("A submission well after the deadline discards the late answers")
    void lateSubmissionDiscarded() {
        QuizAttempt late = attempt(STUDENT, Instant.now().minusSeconds(600));
        when(scorer.finish(any(), any(), eq(true))).thenAnswer(inv -> {
            QuizAttempt a = inv.getArgument(0);
            a.complete(0, 2, 40, Instant.now(), true);
            return a;
        });

        var result = service.submit(9L, answers(10L, 100L));

        verify(answerRepository, never()).saveAll(anyList());
        verify(scorer).finish(late, quiz, true);
        assertThat(result.status()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("A submission within the grace minute is accepted and scored normally")
    void submissionWithinGrace() {
        QuizAttempt justLate = attempt(STUDENT, Instant.now().minusSeconds(20));
        when(scorer.finish(any(), any(), eq(false))).thenAnswer(inv -> {
            QuizAttempt a = inv.getArgument(0);
            a.complete(1, 2, 40, Instant.now(), false);
            return a;
        });

        var result = service.submit(9L, answers(10L, 100L));

        verify(answerRepository).saveAll(anyList());
        verify(scorer).finish(justLate, quiz, false);
        assertThat(result.status()).isEqualTo("SUBMITTED");
    }

    // ------------------------------------------------------------------
    // Typed answers and coding questions
    // ------------------------------------------------------------------

    private QuizQuestion shortAnswer(long id) {
        QuizQuestion q = QuizQuestion.builder().id(id).quizId(1L).type(QuestionType.SHORT_ANSWER)
                .marks(2).sequenceNo((int) id).questionText("capital of France?").build();
        q.getAcceptedAnswers().add("Paris");
        return q;
    }

    private QuizQuestion coding(long id) {
        QuizQuestion q = QuizQuestion.builder().id(id).quizId(1L).type(QuestionType.CODING)
                .codeLanguage(com.itilms.common.code.CodeLanguage.PYTHON)
                .marks(4).sequenceNo((int) id).questionText("print the sum").build();
        q.getTestCases().add(QuizTestCase.builder().sequenceNo(1).input("1 2").expectedOutput("3").weight(1).build());
        q.getTestCases().add(QuizTestCase.builder().sequenceNo(2).input("2 2").expectedOutput("4").weight(3).hidden(true).build());
        return q;
    }

    private static SubmitAttemptRequest text(long questionId, String answerText) {
        return new SubmitAttemptRequest(List.of(new SubmitAttemptRequest.Answer(questionId, null, answerText)));
    }

    private static CodingJudge.Verdict verdict(boolean first, boolean second) {
        return new CodingJudge.Verdict(null, List.of(
                new CodingJudge.CaseResult(1, false, first, 1, "1 2", "3", "3", null),
                new CodingJudge.CaseResult(2, true, second, 3, "2 2", "4", "4", null)));
    }

    @Test
    @DisplayName("A typed answer is saved against a short-answer question, and blank text takes it back")
    void typedAnswerSavedAndCleared() {
        QuizQuestion sa = shortAnswer(30L);
        when(questionRepository.findWithOptions(1L)).thenReturn(List.of(q1, sa));
        attempt(STUDENT, Instant.now().plusSeconds(600));

        service.saveAnswers(9L, text(30L, "  paris "));

        org.mockito.ArgumentCaptor<List<QuizAnswer>> saved = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(answerRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(a -> assertThat(a.getAnswerText()).isEqualTo("  paris "));

        QuizAnswer existing = QuizAnswer.builder().attemptId(9L).questionId(30L).answerText("paris").build();
        when(answerRepository.findByAttemptId(9L)).thenReturn(List.of(existing));
        service.saveAnswers(9L, text(30L, "   "));
        verify(answerRepository).deleteAll(List.of(existing));
    }

    @Test
    @DisplayName("Options on a text question, and text on a choice question, are refused")
    void answerShapeMustMatchTheQuestion() {
        QuizQuestion sa = shortAnswer(30L);
        when(questionRepository.findWithOptions(1L)).thenReturn(List.of(q1, sa));
        attempt(STUDENT, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> service.saveAnswers(9L, answers(30L, 100L)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("answered in text");
        assertThatThrownBy(() -> service.saveAnswers(9L, text(10L, "hello")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("choosing options");
        verify(answerRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("A student may answer in another language only when the author allowed it")
    void anotherLanguageNeedsThePermissionOfTheQuestion() {
        QuizQuestion cq = coding(40L);
        when(questionRepository.findWithOptions(1L)).thenReturn(List.of(q1, cq));
        attempt(STUDENT, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> service.runTests(9L, 40L, "print(1)", "JAVA"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("must be answered in");
        verify(judge, never()).judge(any(), any(), any());
        verify(judge, never()).judge(any(), any());
    }

    @Test
    @DisplayName("When allowed, the chosen language is what runs, and it is kept with the answer")
    void chosenLanguageIsRunAndStored() {
        QuizQuestion cq = coding(40L);
        cq.setAllowLanguageChoice(true);
        when(questionRepository.findWithOptions(1L)).thenReturn(List.of(q1, cq));
        attempt(STUDENT, Instant.now().plusSeconds(600));
        when(judge.judge(cq, "class A {}", com.itilms.common.code.CodeLanguage.JAVA)).thenReturn(verdict(true, true));

        service.runTests(9L, 40L, "class A {}", "java");

        org.mockito.ArgumentCaptor<QuizAnswer> saved = org.mockito.ArgumentCaptor.forClass(QuizAnswer.class);
        verify(answerRepository).save(saved.capture());
        assertThat(saved.getValue().getCodeLanguage()).isEqualTo(com.itilms.common.code.CodeLanguage.JAVA);
        assertThat(saved.getValue().getTestedSourceHash())
                .as("the verdict belongs to this code in this language")
                .isEqualTo(CodingJudge.verdictKey("class A {}", com.itilms.common.code.CodeLanguage.JAVA))
                .isNotEqualTo(CodingJudge.sha256("class A {}"));
    }

    @Test
    @DisplayName("SQL cannot be chosen, and naming the question's own language is just the normal run")
    void sqlIsRefusedAndTheOwnLanguageIsNotAChoice() {
        QuizQuestion cq = coding(40L);
        cq.setAllowLanguageChoice(true);
        when(questionRepository.findWithOptions(1L)).thenReturn(List.of(q1, cq));
        attempt(STUDENT, Instant.now().plusSeconds(600));
        when(judge.judge(cq, "print(1)")).thenReturn(verdict(true, true));

        assertThatThrownBy(() -> service.runTests(9L, 40L, "select 1", "SQL"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("SQL");

        service.runTests(9L, 40L, "print(1)", "PYTHON");
        verify(judge).judge(cq, "print(1)");
    }

    @Test
    @DisplayName("Saving an answer with a language the question does not allow is refused")
    void savingAnAnswerChecksTheLanguageToo() {
        QuizQuestion cq = coding(40L);
        when(questionRepository.findWithOptions(1L)).thenReturn(List.of(q1, cq));
        attempt(STUDENT, Instant.now().plusSeconds(600));

        var request = new SubmitAttemptRequest(List.of(new SubmitAttemptRequest.Answer(40L, null, "code", "JAVA")));

        assertThatThrownBy(() -> service.saveAnswers(9L, request))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("must be answered in");
    }

    @Test
    @DisplayName("Running tests keeps the code, the verdict and the marks it earned, tied to that exact code")
    void runTestsStoresTheVerdict() {
        QuizQuestion cq = coding(40L);
        when(questionRepository.findWithOptions(1L)).thenReturn(List.of(q1, cq));
        attempt(STUDENT, Instant.now().plusSeconds(600));
        when(judge.judge(cq, "print(1+2)")).thenReturn(verdict(true, false));

        var response = service.runTests(9L, 40L, "print(1+2)");

        org.mockito.ArgumentCaptor<QuizAnswer> saved = org.mockito.ArgumentCaptor.forClass(QuizAnswer.class);
        verify(answerRepository).save(saved.capture());
        QuizAnswer row = saved.getValue();
        assertThat(row.getAnswerText()).isEqualTo("print(1+2)");
        assertThat(row.getTestsPassed()).isEqualTo(1);
        assertThat(row.getTestsTotal()).isEqualTo(2);
        assertThat(row.getTestedMarks()).isEqualTo(1);   // weight 1 of 4, on a 4-mark question
        assertThat(row.getTestedSourceHash()).isEqualTo(CodingJudge.sha256("print(1+2)"));

        // The hidden case tells the student pass or fail and nothing about it.
        assertThat(response.passed()).isEqualTo(1);
        assertThat(response.cases().get(1)).satisfies(c -> {
            assertThat(c.hidden()).isTrue();
            assertThat(c.input()).isNull();
            assertThat(c.expectedOutput()).isNull();
            assertThat(c.actualOutput()).isNull();
        });
    }

    @Test
    @DisplayName("A refused run (runner busy) saves nothing and leaves the earlier answer alone")
    void refusedRunChangesNothing() {
        QuizQuestion cq = coding(40L);
        when(questionRepository.findWithOptions(1L)).thenReturn(List.of(q1, cq));
        attempt(STUDENT, Instant.now().plusSeconds(600));
        when(judge.judge(any(), any())).thenThrow(new BusinessRuleException("CODE_RUNNER_UNAVAILABLE", "busy"));

        assertThatThrownBy(() -> service.runTests(9L, 40L, "x")).isInstanceOf(BusinessRuleException.class);
        verify(answerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tests can only be run on a coding question of this test, by the attempt's owner")
    void runTestsGuards() {
        attempt(STUDENT, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> service.runTests(9L, 10L, "x"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not a coding question");
        assertThatThrownBy(() -> service.runTests(9L, 999L, "x"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not part of this test");
        verify(judge, never()).judge(any(), any());

        attempt(99L, Instant.now().plusSeconds(600));
        assertThatThrownBy(() -> service.runTests(9L, 10L, "x")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Submitting re-runs the tests for code that changed since its last run")
    void submitRefreshesStaleVerdicts() {
        QuizQuestion cq = coding(40L);
        when(questionRepository.findWithOptions(1L)).thenReturn(List.of(q1, cq));
        QuizAttempt a = attempt(STUDENT, Instant.now().plusSeconds(600));
        QuizAnswer stale = QuizAnswer.builder().attemptId(9L).questionId(40L).answerText("print(3)")
                .testedSourceHash(CodingJudge.sha256("print(2)")).testedMarks(0).build();
        when(answerRepository.findByAttemptId(9L)).thenReturn(List.of(stale));
        when(judge.judge(cq, "print(3)")).thenReturn(verdict(true, true));
        when(scorer.finish(any(), any(), eq(false))).thenAnswer(inv -> {
            a.complete(4, 4, 40, Instant.now(), false);
            return a;
        });

        service.submit(9L, new SubmitAttemptRequest(List.of()));

        assertThat(stale.getTestedMarks()).isEqualTo(4);
        assertThat(stale.getTestedSourceHash()).isEqualTo(CodingJudge.sha256("print(3)"));
    }

    @Test
    @DisplayName("Submitting when the runner refuses still submits, on the last tested version")
    void submitSurvivesARefusedRun() {
        QuizQuestion cq = coding(40L);
        when(questionRepository.findWithOptions(1L)).thenReturn(List.of(q1, cq));
        QuizAttempt a = attempt(STUDENT, Instant.now().plusSeconds(600));
        QuizAnswer stale = QuizAnswer.builder().attemptId(9L).questionId(40L).answerText("print(3)")
                .testedSourceHash(CodingJudge.sha256("print(2)")).testedMarks(2).build();
        when(answerRepository.findByAttemptId(9L)).thenReturn(List.of(stale));
        when(judge.judge(any(), any())).thenThrow(new BusinessRuleException("CODE_RUNNER_UNAVAILABLE", "busy"));
        when(scorer.finish(any(), any(), eq(false))).thenAnswer(inv -> {
            a.complete(2, 4, 40, Instant.now(), false);
            return a;
        });

        var result = service.submit(9L, new SubmitAttemptRequest(List.of()));

        assertThat(result.status()).isEqualTo("SUBMITTED");
        assertThat(stale.getTestedMarks()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Secure test mode
    // ------------------------------------------------------------------

    private static ViolationRequest report(ViolationType type) {
        return new ViolationRequest(type, null, null);
    }

    private QuizAttempt secureAttempt(int limit) {
        quiz.setSecureMode(true);
        quiz.setMaxViolations(limit);
        return attempt(STUDENT, Instant.now().plusSeconds(600));
    }

    @Test
    @DisplayName("The first counted violation warns; the one that reaches the limit ends the attempt")
    void warnsThenTerminates() {
        QuizAttempt a = secureAttempt(2);

        var first = service.recordViolation(9L, report(ViolationType.TAB_SWITCH));
        assertThat(first.counted()).isTrue();
        assertThat(first.terminated()).isFalse();
        assertThat(first.violationCount()).isEqualTo(1);
        assertThat(first.warningsLeft()).isZero();
        assertThat(first.message()).startsWith("Warning: Please do not leave the test window.");
        verify(scorer, never()).terminate(any(), any(), any());

        // Three seconds on, a second leaving: not a repeat report of the first.
        when(violations.findFirstByAttemptIdAndCountedTrueOrderByOccurredAtDesc(9L)).thenReturn(Optional.of(
                QuizViolation.builder().counted(true).occurredAt(Instant.now().minusSeconds(30)).build()));
        when(scorer.terminate(any(), any(), any())).thenAnswer(inv -> {
            a.complete(1, 2, 40, Instant.now(), false);
            a.terminate(inv.getArgument(2));
            return a;
        });

        var second = service.recordViolation(9L, report(ViolationType.WINDOW_BLUR));

        assertThat(second.terminated()).isTrue();
        assertThat(second.violationCount()).isEqualTo(2);
        assertThat(second.message()).contains("ended");
        verify(scorer).terminate(eq(a), eq(quiz), any());
        assertThat(a.getStatus()).isEqualTo(AttemptStatus.TERMINATED);
        assertThat(a.getPassed()).isFalse();
    }

    @Test
    @DisplayName("With a limit of one, the first violation ends the attempt at once")
    void limitOneTerminatesImmediately() {
        secureAttempt(1);

        var outcome = service.recordViolation(9L, report(ViolationType.TAB_SWITCH));

        verify(scorer).terminate(any(), eq(quiz), any());
        assertThat(outcome.maxViolations()).isEqualTo(1);
    }

    @Test
    @DisplayName("A second report of the same leaving (tab hidden, then window blurred) is recorded but counted once")
    void mergesTheSameLeaving() {
        QuizAttempt a = secureAttempt(3);
        when(violations.findFirstByAttemptIdAndCountedTrueOrderByOccurredAtDesc(9L)).thenReturn(Optional.of(
                QuizViolation.builder().counted(true).occurredAt(Instant.now().minusMillis(400)).build()));

        var outcome = service.recordViolation(9L, report(ViolationType.WINDOW_BLUR));

        assertThat(outcome.counted()).isFalse();
        assertThat(a.getViolationCount()).isZero();
        org.mockito.ArgumentCaptor<QuizViolation> saved = org.mockito.ArgumentCaptor.forClass(QuizViolation.class);
        verify(violations).save(saved.capture());
        assertThat(saved.getValue().isCounted()).isFalse();
    }

    @Test
    @DisplayName("Copying, right-clicks and leaving fullscreen are recorded, and never end a test")
    void recordedButNotCounted() {
        QuizAttempt a = secureAttempt(1);

        for (ViolationType type : List.of(ViolationType.COPY_ATTEMPT, ViolationType.PASTE_ATTEMPT,
                ViolationType.RIGHT_CLICK, ViolationType.FULLSCREEN_EXIT, ViolationType.SHORTCUT_BLOCKED)) {
            assertThat(service.recordViolation(9L, report(type)).counted()).isFalse();
        }

        assertThat(a.getViolationCount()).isZero();
        assertThat(a.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        verify(violations, org.mockito.Mockito.times(5)).save(any());
        verify(scorer, never()).terminate(any(), any(), any());
    }

    @Test
    @DisplayName("A camera event on a test that needs the camera is recorded and warns, in the requested words")
    void cameraEventWarnsAndIsRecorded() {
        quiz.setRequireCamera(true);
        attempt(STUDENT, Instant.now().plusSeconds(600));

        var outcome = service.recordViolation(9L, report(ViolationType.FACE_NOT_DETECTED));

        assertThat(outcome.counted()).isFalse();
        assertThat(outcome.terminated()).isFalse();
        assertThat(outcome.message()).isEqualTo("Warning: Please keep your face properly visible in the camera.");
        org.mockito.ArgumentCaptor<QuizViolation> saved = org.mockito.ArgumentCaptor.forClass(QuizViolation.class);
        verify(violations).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(ViolationType.FACE_NOT_DETECTED);
        assertThat(saved.getValue().isCounted()).isFalse();
    }

    @Test
    @DisplayName("Camera events never end an attempt, however many there are and however low the limit")
    void cameraEventsNeverTerminate() {
        quiz.setRequireCamera(true);
        quiz.setSecureMode(true);
        quiz.setMaxViolations(1);
        QuizAttempt a = attempt(STUDENT, Instant.now().plusSeconds(600));

        for (ViolationType type : List.of(ViolationType.FACE_NOT_DETECTED, ViolationType.MULTIPLE_FACES,
                ViolationType.CAMERA_DISABLED, ViolationType.CAMERA_PERMISSION_DENIED, ViolationType.FACE_NOT_DETECTED)) {
            var outcome = service.recordViolation(9L, report(type));
            assertThat(outcome.message()).isEqualTo(type.warning()).startsWith("Warning:");
            assertThat(outcome.terminated()).isFalse();
        }

        assertThat(a.getViolationCount()).isZero();
        assertThat(a.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        verify(scorer, never()).terminate(any(), any(), any());
        verify(violations, org.mockito.Mockito.times(5)).save(any());
    }

    @Test
    @DisplayName("A camera event on a test that does not use the camera is ignored")
    void cameraEventIgnoredWithoutCamera() {
        quiz.setSecureMode(true);
        attempt(STUDENT, Instant.now().plusSeconds(600));

        var outcome = service.recordViolation(9L, report(ViolationType.FACE_NOT_DETECTED));

        assertThat(outcome.message()).isNull();
        verify(violations, never()).save(any());
    }

    @Test
    @DisplayName("Window events on a camera-only test are ignored: it is not a secure test")
    void windowEventIgnoredOnCameraOnlyTest() {
        quiz.setRequireCamera(true);
        QuizAttempt a = attempt(STUDENT, Instant.now().plusSeconds(600));

        var outcome = service.recordViolation(9L, report(ViolationType.TAB_SWITCH));

        assertThat(outcome.counted()).isFalse();
        assertThat(a.getViolationCount()).isZero();
        verify(violations, never()).save(any());
    }

    @Test
    @DisplayName("The trainer's sheet carries the number of camera events per attempt, in one query")
    void resultsSheetCountsCameraEvents() {
        quiz.setRequireCamera(true);
        QuizAttempt done = attempt(STUDENT, Instant.now().minusSeconds(60));
        done.complete(1, 2, 40, Instant.now(), false);
        when(attemptRepository.findByQuizIdAndStatusInOrderByScoreDesc(eq(1L), anyList())).thenReturn(List.of(done));
        when(violations.countByAttemptAndTypes(anyList(), anyList())).thenReturn(List.<Object[]>of(new Object[] {9L, 3L}));

        var sheet = service.quizResults(1L);

        assertThat(sheet).singleElement().satisfies(row -> assertThat(row.cameraEventCount()).isEqualTo(3));
        verify(violations).countByAttemptAndTypes(eq(List.of(9L)), anyList());
    }

    @Test
    @DisplayName("On a test that is not secure, a report is accepted and changes nothing")
    void ignoredWhenNotSecure() {
        QuizAttempt a = attempt(STUDENT, Instant.now().plusSeconds(600));

        var outcome = service.recordViolation(9L, report(ViolationType.TAB_SWITCH));

        assertThat(outcome.counted()).isFalse();
        assertThat(a.getViolationCount()).isZero();
        verify(violations, never()).save(any());
        verify(scorer, never()).terminate(any(), any(), any());
    }

    @Test
    @DisplayName("A report after the attempt has ended does nothing, and says whether it was terminated")
    void ignoredOnceOver() {
        QuizAttempt a = secureAttempt(2);
        a.complete(1, 2, 40, Instant.now(), false);
        a.terminate("too many");

        var outcome = service.recordViolation(9L, report(ViolationType.TAB_SWITCH));

        assertThat(outcome.terminated()).isTrue();
        assertThat(outcome.counted()).isFalse();
        verify(violations, never()).save(any());
    }

    @Test
    @DisplayName("Only the student who owns the attempt can report on it")
    void violationsAreOwnerOnly() {
        secureAttempt(2);
        attempt(99L, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> service.recordViolation(9L, report(ViolationType.TAB_SWITCH)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(violations, never()).save(any());
    }

    @Test
    @DisplayName("The trainer's review of an attempt needs the right to manage that test")
    void reviewNeedsManagerRights() {
        secureAttempt(2);
        org.mockito.Mockito.doThrow(new com.itilms.common.exception.ForbiddenOperationException("no"))
                .when(access).requireManagesQuiz(quiz);

        assertThatThrownBy(() -> service.violations(9L))
                .isInstanceOf(com.itilms.common.exception.ForbiddenOperationException.class);
        verify(violations, never()).findByAttemptIdOrderByOccurredAtAsc(any());
    }

    @Test
    @DisplayName("A resumed paper carries what was already saved, and never a hidden test case")
    void paperCarriesSavedAnswersButNotHiddenCases() {
        QuizQuestion cq = coding(40L);
        when(questionRepository.findWithOptions(1L)).thenReturn(List.of(q1, cq));
        attempt(STUDENT, Instant.now().plusSeconds(600));
        when(answerRepository.findByAttemptId(9L)).thenReturn(List.of(
                QuizAnswer.builder().attemptId(9L).questionId(40L).answerText("print(1)").testsPassed(1).testsTotal(2).build()));

        var paper = service.paper(9L);

        var codingQuestion = paper.questions().stream().filter(q -> q.id() == 40L).findFirst().orElseThrow();
        assertThat(codingQuestion.sampleTests()).hasSize(1);
        assertThat(codingQuestion.sampleTests().get(0).expectedOutput()).isEqualTo("3");
        assertThat(codingQuestion.hiddenTestCount()).isEqualTo(1);
        assertThat(paper.savedAnswers()).singleElement().satisfies(s -> {
            assertThat(s.answerText()).isEqualTo("print(1)");
            assertThat(s.testsPassed()).isEqualTo(1);
        });
    }
}