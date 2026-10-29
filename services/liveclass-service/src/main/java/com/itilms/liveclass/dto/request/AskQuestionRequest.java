package com.itilms.liveclass.dto.request;

import java.util.List;

import com.itilms.liveclass.entity.QuestionType;

import jakarta.validation.Valid;
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
        Integer marks,
        /** Code shown above the question ("what does this print?"). */
        @Size(max = 20000) String snippet,
        String snippetLanguage,
        /** A small database shown as tables ("write the query"). */
        List<DataTable> dataset,
        /** CODING: what a correct program prints for each input. Kept for tests; a live class does not run them. */
        @Valid @Size(max = 10, message = "At most 10 test cases") List<CodeTestCase> testCases,
        /** CODING in SQL: CREATE TABLE and INSERT statements that run before the student's query. */
        @Size(max = 50000) String setupSql,
        Boolean allowLanguageChoice) {

    /** The shape every earlier caller used, before test cases existed. */
    public AskQuestionRequest(QuestionType type, String prompt, List<String> options, List<Integer> correctOptions,
                              List<String> acceptedAnswers, String language, String starterCode, String explanation,
                              Integer marks, String snippet, String snippetLanguage, List<DataTable> dataset) {
        this(type, prompt, options, correctOptions, acceptedAnswers, language, starterCode, explanation, marks,
                snippet, snippetLanguage, dataset, null, null, null);
    }

    /** One input and the output a correct program prints for it. */
    public record CodeTestCase(@Size(max = 10000) String input,
                               @NotBlank(message = "Expected output is required") @Size(max = 10000) String expectedOutput,
                               Boolean hidden,
                               Integer weight) {
    }
}
