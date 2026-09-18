package com.itilms.assessment.dto.response;

import java.time.Instant;
import java.util.List;

import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizOption;
import com.itilms.assessment.entity.QuizQuestion;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A test as its author sees it - answer key included.
 *
 * <p>Only ever returned to the trainer who set the test, or to staff. The view
 * a student receives while taking it is {@link AttemptViewResponse}, which has
 * no {@code correct} field at all, so the answers cannot leak by someone
 * forgetting to strip them.
 */
@Schema(description = "A test, including its answer key (trainers and staff only)")
public record QuizResponse(
        Long id,
        Long courseId,
        Long batchId,
        String title,
        String instructions,
        int durationMinutes,
        int passPercentage,
        int attemptsAllowed,
        int totalMarks,
        Instant availableFrom,
        Instant availableUntil,
        boolean shuffleQuestions,
        boolean showResultImmediately,
        boolean mandatory,
        String status,
        Long trainerId,
        Instant publishedAt,
        int questionCount,

        @Schema(description = "Present on the single-test view only")
        List<Question> questions
) {

    @Schema(description = "A question with its answer key")
    public record Question(Long id, String questionText, String type, int marks,
                           int sequenceNo, String explanation, List<Option> options) {

        static Question from(QuizQuestion q) {
            return new Question(q.getId(), q.getQuestionText(), q.getType().name(), q.getMarks(),
                    q.getSequenceNo(), q.getExplanation(),
                    q.getOptions().stream().map(Option::from).toList());
        }
    }

    public record Option(Long id, String optionText, boolean correct, int sequenceNo) {

        static Option from(QuizOption o) {
            return new Option(o.getId(), o.getOptionText(), o.isCorrect(), o.getSequenceNo());
        }
    }

    public static QuizResponse summary(Quiz q, int questionCount) {
        return build(q, questionCount, null);
    }

    public static QuizResponse detail(Quiz q, List<QuizQuestion> questions) {
        return build(q, questions.size(), questions.stream().map(Question::from).toList());
    }

    private static QuizResponse build(Quiz q, int questionCount, List<Question> questions) {
        return new QuizResponse(
                q.getId(), q.getCourseId(), q.getBatchId(), q.getTitle(), q.getInstructions(),
                q.getDurationMinutes(), q.getPassPercentage(), q.getAttemptsAllowed(), q.getTotalMarks(),
                q.getAvailableFrom(), q.getAvailableUntil(), q.isShuffleQuestions(),
                q.isShowResultImmediately(), q.isMandatory(), q.getStatus().name(), q.getTrainerId(),
                q.getPublishedAt(), questionCount, questions);
    }
}
