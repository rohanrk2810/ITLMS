package com.itilms.assessment.dto.response;

import java.time.Instant;
import java.util.List;

import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizAttempt;
import com.itilms.assessment.entity.QuizQuestion;

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
        List<Question> questions
) {

    @Schema(description = "A question as the student sees it - no answer key")
    public record Question(Long id, String questionText, String type, int marks, List<Option> options) {
    }

    public record Option(Long id, String optionText) {
    }

    public static AttemptViewResponse of(QuizAttempt attempt, Quiz quiz,
                                         List<QuizQuestion> questions, Instant now) {
        List<Question> paper = questions.stream()
                .map(q -> new Question(q.getId(), q.getQuestionText(), q.getType().name(), q.getMarks(),
                        q.getOptions().stream()
                                .map(o -> new Option(o.getId(), o.getOptionText()))
                                .toList()))
                .toList();

        return new AttemptViewResponse(
                attempt.getId(), quiz.getId(), quiz.getTitle(), quiz.getInstructions(),
                attempt.getAttemptNo(), quiz.getAttemptsAllowed(), quiz.getTotalMarks(),
                quiz.getPassPercentage(), attempt.getStartedAt(), attempt.getExpiresAt(),
                attempt.secondsRemaining(now), paper);
    }
}
