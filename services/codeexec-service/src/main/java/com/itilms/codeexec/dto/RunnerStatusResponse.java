package com.itilms.codeexec.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Staff-facing health check of the sandbox and of this service's language mapping. */
@Schema(description = "Is the sandbox there, and does it know every language we mapped?")
public record RunnerStatusResponse(
        boolean configured,
        boolean reachable,
        String message,
        List<LanguageStatus> languages
) {

    public record LanguageStatus(
            String code,
            String label,
            @Schema(description = "Judge0 language id from configuration; null when switched off or the engine has no ids")
            Integer languageId,
            @Schema(description = "What the language is mapped to, e.g. \"id 91\" (Judge0) or \"java 15.0.2\" (Piston); null when switched off")
            String mapping,
            @Schema(description = "What the sandbox calls it, e.g. \"Java (OpenJDK 13.0.1)\"; null when it does not know it")
            String sandboxName,
            @Schema(description = "True when the language can be run right now")
            boolean available
    ) {
    }
}
