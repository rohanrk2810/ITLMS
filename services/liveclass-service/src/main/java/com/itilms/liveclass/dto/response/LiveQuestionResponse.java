package com.itilms.liveclass.dto.response;

import java.time.Instant;
import java.util.List;

import com.itilms.liveclass.entity.QuestionType;

/**
 * A live question as one caller may see it. The answer key ({@code correctOptions}, {@code acceptedAnswers},
 * {@code explanation}) is present for hosts, and for a student only once the question is closed or they have answered.
 */
public record LiveQuestionResponse(
        Long id,
        Long liveSessionId,
        Long classSessionId,
        QuestionType type,
        String prompt,
        List<String> options,
        String language,
        String starterCode,
        int marks,
        String status,
        Instant askedAt,
        int offsetSeconds,
        String offsetLabel,
        List<Integer> correctOptions,
        List<String> acceptedAnswers,
        String explanation,
        Integer answered,
        Integer correctCount,
        MyAnswer myAnswer) {

    public record MyAnswer(List<Integer> selected, String text, String code, String language, Boolean correct,
                           Integer awardedMarks, boolean viaRecording, Instant submittedAt) {
    }
}
