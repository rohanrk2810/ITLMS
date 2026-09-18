package com.itilms.assessment.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Acknowledges saved answers without revealing anything about them.
 *
 * <p>Returns how many questions have an answer and how long is left - enough
 * for the page to show "saved" and keep its timer honest - and nothing about
 * whether any answer is right.
 */
@Schema(description = "Answers saved; the attempt is still running")
public record AnswerSaveResponse(
        Long attemptId,
        int answeredCount,
        int questionCount,
        Instant expiresAt,
        int secondsRemaining
) {
}
