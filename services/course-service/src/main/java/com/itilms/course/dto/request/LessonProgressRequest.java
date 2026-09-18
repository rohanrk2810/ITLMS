package com.itilms.course.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

/**
 * A student reporting how far they have got (Doc S7.2).
 *
 * <p>Sent periodically while a video plays, and once when they tick "mark as
 * complete". The two are separate fields because watching most of a video is
 * not the same as finishing the lesson, and a student who reads a PDF has no
 * watch time at all.
 */
@Schema(description = "Record progress against a lesson")
public record LessonProgressRequest(

        @Schema(description = "Furthest position reached, in seconds. Never moves backwards.")
        @Min(value = 0, message = "Watched seconds cannot be negative")
        Integer watchedSeconds,

        @Schema(description = "True to mark the lesson finished, false to un-mark it")
        Boolean completed
) {
}
