package com.itilms.assessment.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How one coding answer was typed, for the trainer to replay")
public record TypingReplayResponse(
        Long questionId,
        String questionText,
        @Schema(description = "What the editor held before the student typed anything")
        String starterCode,
        @Schema(description = "What the student last saved or submitted, to compare with what the replay ends with")
        String finalCode,
        @Schema(description = "In order. Each 'events' is a JSON array of [time_ms, offset, removed_length, inserted_text, pasted]")
        List<Chunk> chunks
) {
    public record Chunk(int seq, String events) {
    }
}
