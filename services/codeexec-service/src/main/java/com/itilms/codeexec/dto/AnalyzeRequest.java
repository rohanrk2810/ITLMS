package com.itilms.codeexec.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Code to read and estimate. It is never executed. */
@Schema(description = "Code to analyse for time and space complexity")
public record AnalyzeRequest(
        @Schema(example = "JAVA", allowableValues = {"JAVA", "PYTHON", "C", "CPP", "CSHARP"})
        @NotBlank(message = "Choose a language")
        String language,
        @NotBlank(message = "There is no code to analyse")
        String sourceCode
) {
}
