package com.itilms.assessment.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.assessment.entity.QuestionType;
import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizAnswer;
import com.itilms.assessment.entity.QuizAttempt;
import com.itilms.assessment.entity.QuizOption;
import com.itilms.assessment.entity.QuizQuestion;
import com.itilms.assessment.entity.QuizStatus;

/**
 * The answer key must never reach a student before they may see it (Doc S14).
 *
 * <p>Tested on the JSON the server would actually send, not on the Java types:
 * a student reads the bytes on the wire, so that is what has to be clean.
 */
class AnswerKeyExposureTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private final Quiz quiz = Quiz.builder().id(1L).courseId(1L).title("Java basics")
            .durationMinutes(30).totalMarks(2).passPercentage(50)
            .status(QuizStatus.PUBLISHED).showResultImmediately(false).build();

    private final QuizQuestion question = question();

    private final QuizAttempt attempt = QuizAttempt.builder().id(9L).quizId(1L).studentId(3L)
            .attemptNo(1).startedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(1800)).build();

    private static QuizQuestion question() {
        QuizQuestion q = QuizQuestion.builder().id(5L).quizId(1L).type(QuestionType.SINGLE_CHOICE)
                .marks(2).sequenceNo(1).questionText("Which keyword makes a constant?")
                .explanation("final prevents reassignment").build();
        q.addOption(QuizOption.builder().id(51L).optionText("static").correct(false).sequenceNo(1).build());
        q.addOption(QuizOption.builder().id(52L).optionText("final").correct(true).sequenceNo(2).build());
        return q;
    }

    @Test
    @DisplayName("The paper a student sits carries no correct flags and no explanations")
    void paperHasNoKey() throws Exception {
        String body = json.writeValueAsString(AttemptViewResponse.of(attempt, quiz, List.of(question), Instant.now()));

        assertThat(body).contains("Which keyword makes a constant?", "\"final\"");
        assertThat(body).doesNotContain("correct").doesNotContain("explanation")
                .doesNotContain("final prevents reassignment");
    }

    @Test
    @DisplayName("A held-back result reveals neither the score nor the key")
    void heldBackResultIsBlank() throws Exception {
        attempt.complete(2, 2, 50, Instant.now(), false);
        String body = json.writeValueAsString(
                AttemptResultResponse.of(attempt, quiz, false, List.of(question), List.of()));

        assertThat(body).contains("\"resultVisible\":false", "\"score\":null", "\"passed\":null");
        assertThat(body).doesNotContain("correctOptionIds").doesNotContain("final prevents reassignment");
    }

    @Test
    @DisplayName("Once results are released, the breakdown shows what was right")
    void releasedResultShowsKey() throws Exception {
        attempt.complete(2, 2, 50, Instant.now(), false);
        QuizAnswer answer = QuizAnswer.builder().attemptId(9L).questionId(5L)
                .selectedOptionIds(new java.util.LinkedHashSet<>(Set.of(52L))).correct(true).marksAwarded(2).build();

        AttemptResultResponse result = AttemptResultResponse.of(attempt, quiz, true, List.of(question), List.of(answer));

        assertThat(result.score()).isEqualTo(2);
        assertThat(result.answers()).singleElement().satisfies(a -> {
            assertThat(a.correctOptionIds()).containsExactly(52L);
            assertThat(a.correct()).isTrue();
            assertThat(a.explanation()).isEqualTo("final prevents reassignment");
        });
    }
}
