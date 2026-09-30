package com.itilms.liveclass.dto.response;

import java.time.Instant;
import java.util.List;

/** One student's answer, for the trainer. */
public record LiveAnswerResponse(Long userId, Long studentId, String displayName, List<Integer> selected,
                                 String text, String code, String language, Boolean correct, Integer awardedMarks,
                                 boolean viaRecording, Instant submittedAt) {
}
