package com.itilms.liveclass.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A question to keep in the bank: a name, how to find it again, and the question exactly as it would be asked. */
public record MaterialRequest(
        @NotBlank(message = "Give the material a title") @Size(max = 160) String title,
        @NotNull(message = "The question is missing") @Valid AskQuestionRequest question,
        Long courseId,
        @Size(max = 120) String topic,
        @Size(max = 10, message = "At most 10 tags") List<@Size(max = 40) String> tags,
        @Pattern(regexp = "^$|EASY|MEDIUM|HARD", message = "Difficulty is EASY, MEDIUM or HARD") String difficulty,
        Boolean shared,
        /** Where it is filed, like Java/OOPs/Coding. Blank means the top level. */
        @Size(max = 300) String folder) {
}
