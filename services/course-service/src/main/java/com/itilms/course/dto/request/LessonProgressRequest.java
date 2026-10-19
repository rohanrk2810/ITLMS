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
        Boolean completed,

        @Schema(description = "Seconds actually played (not skipped), cumulative. Only moves forward.")
        @Min(value = 0, message = "Played seconds cannot be negative")
        Integer playedSeconds,

        @Schema(description = "Length of the video, as the player reports it")
        @Min(value = 0, message = "Duration cannot be negative")
        Integer durationSeconds,

        @Schema(description = "Seconds the camera check found no face while playing, cumulative")
        @Min(value = 0, message = "Face-away seconds cannot be negative")
        Integer faceAwaySeconds,

        @Schema(description = "Times the video was paused for a missing face, cumulative")
        @Min(value = 0, message = "Pauses cannot be negative")
        Integer facePauses
) {

    /** A report with no playback detail (an older client). */
    public LessonProgressRequest(Integer watchedSeconds, Boolean completed) {
        this(watchedSeconds, completed, null, null, null, null);
    }
}
