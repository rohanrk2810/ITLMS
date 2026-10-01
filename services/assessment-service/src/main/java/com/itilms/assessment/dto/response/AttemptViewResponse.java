package com.itilms.assessment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizAnswer;
import com.itilms.assessment.entity.QuizAttempt;
import com.itilms.assessment.entity.QuizQuestion;
import com.itilms.assessment.entity.QuizTestCase;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The test paper, as handed to a student who is sitting it.
 *
 * <p>There is no {@code correct} field anywhere in this type, and no
 * explanation text. That is the point: a student can read every byte the server
 * sends them, so the answer key must not be in it. Scoring happens on the
 * server from the stored key when the attempt is submitted (Doc S14).
 *
 * <p>{@code secondsRemaining} is computed from the server's deadline. The
 * countdown in the browser is a convenience; the deadline that counts is
 * {@code expiresAt}.
 */
@Schema(description = "A test paper for a student in the middle of an attempt")
public record AttemptViewResponse(
        Long attemptId,
        Long quizId,
        String title,
        String instructions,
        int attemptNo,
        int attemptsAllowed,
        int totalMarks,
        int passPercentage,
        Instant startedAt,

        @Schema(description = "When this sitting closes, decided by the server")
        Instant expiresAt,

        int secondsRemaining,

        @Schema(description = "Secure test mode is on: the browser must report leaving the window")
        boolean secureMode,
        int maxViolations,
        @Schema(description = "The camera must stay on and show the student's face")
        boolean requireCamera,
        boolean requireMicrophone,
        @Schema(description = "Violations that have counted so far in this attempt")
        int violationCount,
        List<Question> questions,

        @Schema(description = "What this attempt already has saved, so a resumed sitting shows it again")
        List<SavedAnswer> savedAnswers
) {

    @Schema(description = "A question as the student sees it - no answer key")
    public record Question(Long id, String questionText, String type, int marks, List<Option> options,
                           @Schema(description = "CODING only") String codeLanguage,
                           String starterCode,
                           @Schema(description = "CODING only: the cases the student may see. Hidden ones are not sent.")
                           List<SampleTest> sampleTests,
                           int hiddenTestCount,
                           @Schema(description = "CODING only: the student may pick the language") boolean allowLanguageChoice) {
    }

    public record SampleTest(String input, String expectedOutput) {
    }

    public record SavedAnswer(Long questionId, Set<Long> selectedOptionIds, String answerText,
                              Integer testsPassed, Integer testsTotal, String codeLanguage) {
    }

    public record Option(Long id, String optionText) {
    }

    public static AttemptViewResponse of(QuizAttempt attempt, Quiz quiz,
                                         List<QuizQuestion> questions, List<QuizAnswer> saved, Instant now) {
        List<Question> paper = questions.stream()
                .map(q -> new Question(q.getId(), q.getQuestionText(), q.getType().name(), q.getMarks(),
                        q.getOptions().stream()
                                .map(o -> new Option(o.getId(), o.getOptionText()))
                                .toList(),
                        q.getCodeLanguage() == null ? null : q.getCodeLanguage().name(),
                        q.getStarterCode(),
                        q.getTestCases().stream().filter(c -> !c.isHidden())
                                .map(c -> new SampleTest(c.getInput(), c.getExpectedOutput())).toList(),
                        (int) q.getTestCases().stream().filter(QuizTestCase::isHidden).count(),
                        q.isAllowLanguageChoice()))
                .toList();
        List<SavedAnswer> savedAnswers = saved.stream()
                .map(a -> new SavedAnswer(a.getQuestionId(), Set.copyOf(a.getSelectedOptionIds()), a.getAnswerText(),
                        a.getTestsPassed(), a.getTestsTotal(),
                        a.getCodeLanguage() == null ? null : a.getCodeLanguage().name()))
                .toList();

        return new AttemptViewResponse(
                attempt.getId(), quiz.getId(), quiz.getTitle(), quiz.getInstructions(),
                attempt.getAttemptNo(), quiz.getAttemptsAllowed(), quiz.getTotalMarks(),
                quiz.getPassPercentage(), attempt.getStartedAt(), attempt.getExpiresAt(),
                attempt.secondsRemaining(now), quiz.isSecureMode(), quiz.getMaxViolations(),
                quiz.isRequireCamera(), quiz.isRequireMicrophone(), attempt.getViolationCount(), paper, savedAnswers);
    }
}
