package com.itilms.assessment.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@Schema(description = "A question and its options")
public record QuestionRequest(

        @NotBlank(message = "Question text is required")
        String questionText,

        @Schema(description = "SINGLE_CHOICE, MULTI_CHOICE or TRUE_FALSE. Default SINGLE_CHOICE.")
        String type,

        @Min(value = 1, message = "A question must be worth at least one mark")
        @Max(value = 100)
        Integer marks,

        @Schema(description = "Shown with the result, never while answering")
        String explanation,

        @Valid
        @NotEmpty(message = "A question needs options")
        @Size(min = 2, max = 8, message = "Between 2 and 8 options")
        List<OptionRequest> options
) {

    @Schema(description = "One choice. At least one option must be marked correct.")
    public record OptionRequest(
            @NotBlank(message = "Option text is required")
            @Size(max = 2000)
            String optionText,

            Boolean correct
    ) {
    }
}
