package com.itilms.liveclass.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.itilms.liveclass.entity.QuestionType;

class QuestionGraderTest {

    @Test
    void singleChoiceNeedsTheOneRightOption() {
        assertThat(QuestionGrader.grade(QuestionType.MCQ, List.of(2), null, List.of(2), null)).isTrue();
        assertThat(QuestionGrader.grade(QuestionType.MCQ, List.of(2), null, List.of(1), null)).isFalse();
        assertThat(QuestionGrader.grade(QuestionType.MCQ, List.of(2), null, List.of(1, 2), null)).isFalse();
        assertThat(QuestionGrader.grade(QuestionType.TRUE_FALSE, List.of(1), null, List.of(1), null)).isTrue();
    }

    @Test
    void multipleSelectNeedsExactlyTheRightSetInAnyOrder() {
        assertThat(QuestionGrader.grade(QuestionType.MULTIPLE_SELECT, List.of(0, 2), null, List.of(2, 0), null)).isTrue();
        assertThat(QuestionGrader.grade(QuestionType.MULTIPLE_SELECT, List.of(0, 2), null, List.of(0), null)).isFalse();
        assertThat(QuestionGrader.grade(QuestionType.MULTIPLE_SELECT, List.of(0, 2), null, List.of(0, 1, 2), null)).isFalse();
    }

    @Test
    void shortAnswerIgnoresCaseAndSpacingAndNeedsAKey() {
        List<String> key = List.of("Hash Map", "HashMap");
        assertThat(QuestionGrader.grade(QuestionType.SHORT_ANSWER, null, key, null, "  hash   map ")).isTrue();
        assertThat(QuestionGrader.grade(QuestionType.SHORT_ANSWER, null, key, null, "hashmap")).isTrue();
        assertThat(QuestionGrader.grade(QuestionType.SHORT_ANSWER, null, key, null, "list")).isFalse();
        assertThat(QuestionGrader.grade(QuestionType.SHORT_ANSWER, null, key, null, "  ")).isFalse();
        assertThat(QuestionGrader.grade(QuestionType.SHORT_ANSWER, null, null, null, "anything")).isNull();
    }

    @Test
    void codingAndOtherQuestionsAreLeftForTheTrainer() {
        assertThat(QuestionGrader.grade(QuestionType.CODING, null, null, null, null)).isNull();
        assertThat(QuestionGrader.grade(QuestionType.OTHER, null, null, null, "x")).isNull();
    }

    @Test
    void offsetIsMeasuredFromTheClassStart() {
        Instant start = Instant.parse("2026-09-25T10:00:00Z");
        int offset = QuestionGrader.offsetSeconds(start, Instant.parse("2026-09-25T10:35:42Z"));
        assertThat(offset).isEqualTo(2142);
        assertThat(QuestionGrader.label(offset)).isEqualTo("00:35:42");
        assertThat(QuestionGrader.label(3661)).isEqualTo("01:01:01");
    }

    @Test
    void aQuestionBeforeTheStartIsAtZero() {
        Instant start = Instant.parse("2026-09-25T10:00:00Z");
        assertThat(QuestionGrader.offsetSeconds(start, Instant.parse("2026-09-25T09:58:00Z"))).isZero();
    }
}
