package com.itilms.liveclass.dto.request;

import java.util.List;

import jakarta.validation.constraints.Size;

/** A student's answer: which options they picked, or the text or code they wrote. */
public record AnswerQuestionRequest(
        List<Integer> selected,
        @Size(max = 4000) String text,
        @Size(max = 50000) String code,
        String language) {
}
