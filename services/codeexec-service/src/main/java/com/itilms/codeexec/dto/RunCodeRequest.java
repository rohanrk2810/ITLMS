package com.itilms.codeexec.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Run this code. Size limits are configuration, so the service checks them, not annotations. */
@Schema(description = "Code to run")
public record RunCodeRequest(

        @Schema(example = "PYTHON", allowableValues = {"JAVA", "PYTHON", "C", "CPP", "CSHARP", "SQL"})
        @NotBlank(message = "Choose a language")
        String language,

        @NotBlank(message = "There is no code to run")
        String sourceCode,

        @Schema(description = "Fed to the program's standard input. Optional.")
        String stdin
) {
}
