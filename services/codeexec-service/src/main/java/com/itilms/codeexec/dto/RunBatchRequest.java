package com.itilms.codeexec.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/** One program, several inputs. Used to grade coding questions; size limits are checked by the service. */
@Schema(description = "Run one program against several inputs")
public record RunBatchRequest(

        @Schema(example = "JAVA", allowableValues = {"JAVA", "PYTHON", "C", "CPP", "CSHARP", "SQL"})
        @NotBlank(message = "Choose a language")
        String language,

        @NotBlank(message = "There is no code to run")
        String sourceCode,

        @Schema(description = "One entry per run, fed to standard input. Use an empty string for no input.")
        @NotEmpty(message = "Give at least one input")
        List<String> stdins
) {
}
