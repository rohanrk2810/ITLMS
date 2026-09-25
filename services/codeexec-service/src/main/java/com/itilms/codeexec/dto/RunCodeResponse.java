package com.itilms.codeexec.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What happened when the code ran.
 *
 * <p>A compile error or a crash is a normal answer (HTTP 200, with the outcome
 * saying so) - it is the student's code that failed, not this API.
 */
@Schema(description = "Result of one run")
public record RunCodeResponse(
        String language,
        @Schema(allowableValues = {"SUCCESS", "COMPILE_ERROR", "RUNTIME_ERROR", "TIME_LIMIT_EXCEEDED"})
        String outcome,
        @Schema(description = "The sandbox's own wording, e.g. \"Runtime Error (NZEC)\"")
        String statusDescription,
        String stdout,
        String stderr,
        @Schema(description = "The compiler's messages when the program did not compile")
        String compileOutput,
        @Schema(description = "Extra note from the sandbox, e.g. \"Exited with error status 1\"")
        String message,
        Double timeSeconds,
        Integer memoryKb,
        @Schema(description = "True when stdout/stderr was cut at the configured limit")
        boolean outputTruncated
) {
}
