package com.itilms.assessment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Question pool: each attempt draws its own questions once, and everything after counts only those. */
class QuestionPoolTest {

    private static QuizQuestion question(long id, int marks) {
        return QuizQuestion.builder().id(id).quizId(1L).type(QuestionType.SINGLE_CHOICE).marks(marks)
                .sequenceNo((int) id).questionText("q" + id).build();
    }

    private final List<QuizQuestion> all = List.of(question(1, 1), question(2, 2), question(3, 3), question(4, 4), question(5, 5));

    @Test
    @DisplayName("An attempt without a pool is about every question")
    void noPoolMeansAll() {
        QuizAttempt attempt = QuizAttempt.builder().build();

        assertThat(attempt.paperFrom(all)).isEqualTo(all);
        assertThat(attempt.maxMarksOr(15)).isEqualTo(15);
    }

    @Test
    @DisplayName("A draw takes the asked number of different questions and is worth exactly their marks")
    void drawIsTheAskedSizeAndWorthItsMarks() {
        QuizAttempt attempt = QuizAttempt.builder().build();

        attempt.drawPaper(all, 3);

        List<QuizQuestion> drawn = attempt.paperFrom(all);
        assertThat(drawn).hasSize(3).doesNotHaveDuplicates();
        assertThat(attempt.maxMarksOr(15)).isEqualTo(drawn.stream().mapToInt(QuizQuestion::getMarks).sum());
    }

    @Test
    @DisplayName("The drawn paper stays the same on every later look, in the test's own order")
    void drawnPaperIsStable() {
        QuizAttempt attempt = QuizAttempt.builder().build();
        attempt.drawPaper(all, 2);

        List<Long> first = attempt.paperFrom(all).stream().map(QuizQuestion::getId).toList();
        List<Long> second = attempt.paperFrom(all).stream().map(QuizQuestion::getId).toList();

        assertThat(second).isEqualTo(first);
        assertThat(first).isSorted();
    }

    @Test
    @DisplayName("Different students do not all get the same questions")
    void studentsGetDifferentDraws() {
        Set<List<Long>> draws = new java.util.HashSet<>();
        for (int i = 0; i < 40; i++) {
            QuizAttempt attempt = QuizAttempt.builder().build();
            attempt.drawPaper(all, 2);
            draws.add(attempt.paperFrom(all).stream().map(QuizQuestion::getId).collect(Collectors.toList()));
        }

        assertThat(draws.size()).isGreaterThan(3);
    }

    @Test
    @DisplayName("Asking for more than the test has gives every question")
    void drawLargerThanTheTest() {
        QuizAttempt attempt = QuizAttempt.builder().build();

        attempt.drawPaper(all, 50);

        assertThat(attempt.paperFrom(all)).hasSize(5);
    }
}
