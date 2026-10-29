package com.itilms.assessment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A batch of edits to one coding answer")
public record TypingChunkRequest(

        @Schema(description = "The browser numbers its batches from 0 for each question; one sent twice is kept once")
        @Min(0)
        int seq,

        @Schema(description = "A JSON array of edits: [time_ms, offset, removed_length, inserted_text, pasted(0|1)]")
        @NotBlank @Size(max = 100000)
        String events
) {
}
