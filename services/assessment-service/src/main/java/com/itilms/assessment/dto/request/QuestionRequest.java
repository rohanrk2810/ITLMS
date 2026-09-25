package com.itilms.assessment.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A question. Which of the fields after {@code explanation} apply depends on {@code type}: options for the
 * choice types, accepted answers for SHORT_ANSWER, language / starter code / test cases for CODING. The
 * service checks the combination; a field that does not belong to the type is refused rather than ignored.
 */
@Schema(description = "A question and, depending on its type, its options, accepted answers or test cases")
public record QuestionRequest(

        @NotBlank(message = "Question text is required")
        String questionText,

        @Schema(description = "SINGLE_CHOICE, MULTI_CHOICE, TRUE_FALSE, SHORT_ANSWER or CODING. Default SINGLE_CHOICE.")
        String type,

        @Min(value = 1, message = "A question must be worth at least one mark")
        @Max(value = 100)
        Integer marks,

        @Schema(description = "Shown with the result, never while answering")
        String explanation,

        @Valid
        @Size(max = 8, message = "At most 8 options")
        @Schema(description = "Choice types only: between 2 and 8, at least one correct")
        List<OptionRequest> options,

        @Schema(description = "CODING only: JAVA, PYTHON, C, CPP, CSHARP or SQL")
        String codeLanguage,

        @Schema(description = "CODING only: what the editor starts with")
        @Size(max = 20000, message = "Starter code is limited to 20000 characters")
        String starterCode,

        @Schema(description = "SHORT_ANSWER only: any of these earns the marks (spaces and case are ignored)")
        @Size(max = 10, message = "At most 10 accepted answers")
        List<@NotBlank @Size(max = 500) String> acceptedAnswers,

        @Valid
        @Schema(description = "CODING only: 1 to 10 cases; the program is marked by the share of weight it passes")
        @Size(max = 10, message = "At most 10 test cases")
        List<TestCaseRequest> testCases
) {

    @Schema(description = "One choice. At least one option must be marked correct.")
    public record OptionRequest(
            @NotBlank(message = "Option text is required")
            @Size(max = 2000)
            String optionText,

            Boolean correct
    ) {
    }

    @Schema(description = "One input and the output a correct program prints for it")
    public record TestCaseRequest(
            @Size(max = 10000)
            String input,

            @NotBlank(message = "Expected output is required")
            @Size(max = 10000)
            String expectedOutput,

            @Schema(description = "A hidden case shows the student only pass or fail")
            Boolean hidden,

            @Min(1) @Max(100)
            Integer weight
    ) {
    }
}
