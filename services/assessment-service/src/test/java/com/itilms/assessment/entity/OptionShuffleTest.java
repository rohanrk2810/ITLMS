package com.itilms.assessment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Answer options are mixed per attempt: neighbours differ, a reload does not move anything. */
class OptionShuffleTest {

    private static QuizQuestion question(QuestionType type, int optionCount) {
        QuizQuestion q = QuizQuestion.builder().id(7L).quizId(1L).type(type).marks(1).sequenceNo(1).questionText("q").build();
        for (int i = 1; i <= optionCount; i++) {
            q.addOption(QuizOption.builder().id(100L + i).optionText("o" + i).correct(i == 1).sequenceNo(i).build());
        }
        return q;
    }

    private static List<Long> ids(List<QuizOption> options) {
        return options.stream().map(QuizOption::getId).toList();
    }

    @Test
    @DisplayName("With shuffling off the options keep the trainer's order")
    void offKeepsOrder() {
        QuizQuestion q = question(QuestionType.SINGLE_CHOICE, 4);

        assertThat(ids(q.optionsFor(9L, false))).containsExactly(101L, 102L, 103L, 104L);
    }

    @Test
    @DisplayName("The same attempt always sees the same order")
    void sameAttemptSameOrder() {
        QuizQuestion q = question(QuestionType.MULTI_CHOICE, 5);

        assertThat(ids(q.optionsFor(9L, true))).isEqualTo(ids(q.optionsFor(9L, true)));
    }

    @Test
    @DisplayName("Different attempts see different orders, with the same options")
    void differentAttemptsDiffer() {
        QuizQuestion q = question(QuestionType.SINGLE_CHOICE, 4);

        Set<List<Long>> orders = new HashSet<>();
        for (long attempt = 1; attempt <= 40; attempt++) {
            List<Long> order = ids(q.optionsFor(attempt, true));
            assertThat(order).containsExactlyInAnyOrder(101L, 102L, 103L, 104L);
            orders.add(order);
        }

        assertThat(orders.size()).isGreaterThan(5);
    }

    @Test
    @DisplayName("True/false keeps True before False, and a text question has no options to mix")
    void trueFalseAndTextUntouched() {
        assertThat(ids(question(QuestionType.TRUE_FALSE, 2).optionsFor(9L, true))).containsExactly(101L, 102L);
        assertThat(question(QuestionType.SHORT_ANSWER, 0).optionsFor(9L, true)).isEmpty();
    }

    @Test
    @DisplayName("Shuffling never changes the stored order the trainer sees")
    void storedOrderUntouched() {
        QuizQuestion q = question(QuestionType.SINGLE_CHOICE, 4);

        q.optionsFor(3L, true);

        assertThat(ids(q.getOptions())).containsExactly(101L, 102L, 103L, 104L);
    }
}
