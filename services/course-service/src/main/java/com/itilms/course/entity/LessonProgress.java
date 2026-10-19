package com.itilms.course.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** How far one student has got through one lesson (Doc S7.2). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lesson_progress")
public class LessonProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(nullable = false)
    @Builder.Default
    private boolean completed = false;

    /**
     * Furthest position reached in a video, in seconds.
     *
     * <p>Only ever moves forward. A student who rewinds to re-watch something
     * has not un-watched it, and letting this go down would make the resume
     * point jump backwards every time they scrubbed.
     */
    @Column(name = "watched_seconds", nullable = false)
    @Builder.Default
    private Integer watchedSeconds = 0;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /** Seconds actually played (not skipped), summed over every viewing. Only moves forward. */
    @Column(name = "played_seconds", nullable = false)
    @Builder.Default
    private Integer playedSeconds = 0;

    /** Length of the video as the student's player reported it. */
    @Column(name = "duration_seconds", nullable = false)
    @Builder.Default
    private Integer durationSeconds = 0;

    /** Seconds the camera check found no face while the video was playing. */
    @Column(name = "face_away_seconds", nullable = false)
    @Builder.Default
    private Integer faceAwaySeconds = 0;

    /** How many times the video was paused because the face went missing. */
    @Column(name = "face_pauses", nullable = false)
    @Builder.Default
    private Integer facePauses = 0;

    /**
     * Folds in a report from the student's player. Each counter only moves forward, and played time can never
     * exceed the length of the video, so a replayed or tampered request cannot take a student past 100%.
     */
    public void recordPlayback(Integer played, Integer duration, Integer faceAway, Integer pauses) {
        if (duration != null && duration > 0) {
            this.durationSeconds = duration;
        }
        if (played != null && played > this.playedSeconds) {
            this.playedSeconds = durationSeconds > 0 ? Math.min(played, durationSeconds) : played;
        }
        if (faceAway != null && faceAway > this.faceAwaySeconds) {
            this.faceAwaySeconds = faceAway;
        }
        if (pauses != null && pauses > this.facePauses) {
            this.facePauses = pauses;
        }
        this.updatedAt = Instant.now();
    }

    /** Share of the video actually watched, 0-100; 0 until a length is known. */
    public int watchedPercent() {
        if (durationSeconds == null || durationSeconds <= 0) {
            return 0;
        }
        return (int) Math.min(100, (long) playedSeconds * 100 / durationSeconds);
    }

    public void recordWatched(int seconds) {
        if (seconds > this.watchedSeconds) {
            this.watchedSeconds = seconds;
        }
        this.updatedAt = Instant.now();
    }

    public void markComplete() {
        if (!this.completed) {
            this.completed = true;
            this.completedAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    public void markIncomplete() {
        this.completed = false;
        this.completedAt = null;
        this.updatedAt = Instant.now();
    }
}
