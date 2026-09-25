package com.itilms.assessment.dto.request;

import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What the student chose.
 *
 * <p>Deliberately carries no marks, no score and no "correct" flag. Everything
 * of that kind is worked out here from the answer key (Doc S14), so there is
 * nothing in this payload worth tampering with.
 */
@Schema(description = "A student's answers for one sitting")
public record SubmitAttemptRequest(

        @Valid
        List<Answer> answers
) {

    @Schema(description = "One question's chosen options. Unanswered questions may be omitted.")
    public record Answer(
            @NotNull(message = "Question id is required")
            Long questionId,

            @Schema(description = "Choice questions: option ids the student ticked. Empty means unanswered.")
            Set<Long> selectedOptionIds,

            @Schema(description = "SHORT_ANSWER: what was typed. CODING: the source code. Blank means unanswered.")
            @Size(max = 50000, message = "An answer is limited to 50000 characters")
            String answerText
    ) {
    }
}
