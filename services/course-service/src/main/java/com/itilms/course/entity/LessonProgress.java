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
