package com.itilms.liveclass.dto.request;

import java.util.List;

import com.itilms.liveclass.entity.QuestionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A question the trainer asks the class. Which fields matter depends on the type. */
public record AskQuestionRequest(
        @NotNull(message = "Choose a question type") QuestionType type,
        @NotBlank(message = "Write the question") @Size(max = 4000) String prompt,
        List<String> options,
        List<Integer> correctOptions,
        List<String> acceptedAnswers,
        String language,
        @Size(max = 20000) String starterCode,
        @Size(max = 4000) String explanation,
        Integer marks) {
}
