package com.itilms.liveclass.dto.response;

import java.time.Instant;
import java.util.List;

import com.itilms.liveclass.dto.request.AskQuestionRequest;

/** A question in the bank. {@code mine} is false for another trainer's shared item. */
public record MaterialResponse(
        Long id,
        String title,
        AskQuestionRequest question,
        Long courseId,
        String topic,
        List<String> tags,
        String difficulty,
        boolean shared,
        boolean mine,
        String ownerName,
        Instant createdAt) {
}
