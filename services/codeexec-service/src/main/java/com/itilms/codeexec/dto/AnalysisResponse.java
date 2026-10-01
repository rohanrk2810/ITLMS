package com.itilms.codeexec.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An estimate of how the code's running time and memory grow with the size of its input, read from the source
 * without running it. It is an estimate: the reason is given so a person can check it, and {@code confidence}
 * says how far to trust it.
 */
@Schema(description = "Estimated time and space complexity of some code, with reasons and suggestions")
public record AnalysisResponse(
        String language,
        @Schema(description = "False for a language that is not analysed (SQL)")
        boolean supported,
        @Schema(description = "Always true: this is an estimate, not a proof")
        boolean estimated,
        @Schema(allowableValues = {"HIGH", "MEDIUM", "LOW"})
        String confidence,
        String timeComplexity,
        Model timeModel,
        String timeReason,
        List<String> timeSteps,
        String spaceComplexity,
        Model spaceModel,
        String spaceReason,
        List<String> spaceSteps,
        @Schema(allowableValues = {"EFFICIENT", "COULD_BE_BETTER", "UNKNOWN"})
        String verdict,
        String verdictMessage,
        List<Suggestion> suggestions,
        List<String> notes
) {

    /** The growth rate in numbers, so a client can draw it: n^(p2/2) * (log n)^log, or exponential. */
    public record Model(int p2, int log, boolean exp) {
    }

    /** A possible improvement. It is advice only: the student's code is never changed. */
    public record Suggestion(
            String title,
            String explanation,
            String currentTime,
            String currentSpace,
            String betterTime,
            String betterSpace,
            Model betterTimeModel,
            Model betterSpaceModel
    ) {
    }
}
