package com.itilms.assessment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizAnswer;
import com.itilms.assessment.entity.QuizAttempt;
import com.itilms.assessment.entity.QuizQuestion;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The outcome of one sitting.
 *
 * <p>How much of it a student sees depends on the test. When the trainer turned
 * off "show result immediately" - usually because the questions will be reused
 * with the next batch - a student sees only that the attempt was received until
 * the test is closed. Handing out the answer key the moment the first student
 * submits would let the rest of the batch copy it. Trainers and staff always
 * see everything.
 */
@Schema(description = "The result of one test attempt")
public record AttemptResultResponse(
        Long attemptId,
        Long quizId,
        String quizTitle,
        Long studentId,
        String studentName,
        int attemptNo,
        String status,
        Instant startedAt,
        Instant submittedAt,

        @Schema(description = "False while results are held back; score fields are then null")
        boolean resultVisible,

        Integer score,
        int totalMarks,
        Integer percentage,
        Boolean passed,
        int passPercentage,

        @Schema(description = "Per-question breakdown, when results are visible")
        List<AnswerResult> answers
) {

    @Schema(description = "One question: what was chosen, what was right, what it was worth")
    public record AnswerResult(
            Long questionId,
            String questionText,
            String type,
            int marks,
            List<AttemptViewResponse.Option> options,
            Set<Long> selectedOptionIds,
            Set<Long> correctOptionIds,
            boolean correct,
            int marksAwarded,
            String explanation,
            @Schema(description = "SHORT_ANSWER: what was typed. CODING: the submitted code.")
            String answerText,
            @Schema(description = "CODING: test cases passed and how many there were")
            Integer testsPassed,
            Integer testsTotal,
            @Schema(description = "SHORT_ANSWER: the accepted answers, shown once results are released")
            List<String> acceptedAnswers
    ) {
    }

    public static AttemptResultResponse of(QuizAttempt attempt, Quiz quiz, boolean visible,
                                           List<QuizQuestion> questions, List<QuizAnswer> answers) {
        List<AnswerResult> breakdown = null;
        if (visible && questions != null) {
            Map<Long, QuizAnswer> byQuestion = answers.stream()
                    .collect(java.util.stream.Collectors.toMap(QuizAnswer::getQuestionId, a -> a));
            breakdown = questions.stream().map(q -> {
                QuizAnswer a = byQuestion.get(q.getId());
                return new AnswerResult(
                        q.getId(), q.getQuestionText(), q.getType().name(), q.getMarks(),
                        q.getOptions().stream()
                                .map(o -> new AttemptViewResponse.Option(o.getId(), o.getOptionText()))
                                .toList(),
                        a == null ? Set.of() : Set.copyOf(a.getSelectedOptionIds()),
                        q.correctOptionIds(),
                        a != null && a.isCorrect(),
                        a == null ? 0 : a.getMarksAwarded(),
                        q.getExplanation(),
                        a == null ? null : a.getAnswerText(),
                        a == null ? null : a.getTestsPassed(),
                        a == null ? null : a.getTestsTotal(),
                        q.getType() == com.itilms.assessment.entity.QuestionType.SHORT_ANSWER
                                ? List.copyOf(q.getAcceptedAnswers()) : List.of());
            }).toList();
        }

        return new AttemptResultResponse(
                attempt.getId(), quiz.getId(), quiz.getTitle(), attempt.getStudentId(),
                attempt.getStudentName(), attempt.getAttemptNo(), attempt.getStatus().name(),
                attempt.getStartedAt(), attempt.getSubmittedAt(), visible,
                visible ? attempt.getScore() : null,
                quiz.getTotalMarks(),
                visible ? attempt.getPercentage() : null,
                visible ? attempt.getPassed() : null,
                quiz.getPassPercentage(),
                breakdown);
    }
}
