package com.itilms.assessment.entity;

import java.time.Instant;

import com.itilms.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An MCQ test (Doc S6.11). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "quizzes")
public class Quiz extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** Null means every batch studying the course can take it. */
    @Column(name = "batch_id")
    private Long batchId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String instructions;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "pass_percentage", nullable = false)
    @Builder.Default
    private int passPercentage = 40;

    @Column(name = "attempts_allowed", nullable = false)
    @Builder.Default
    private int attemptsAllowed = 1;

    @Column(name = "total_marks", nullable = false)
    @Builder.Default
    private int totalMarks = 0;

    @Column(name = "available_from")
    private Instant availableFrom;

    @Column(name = "available_until")
    private Instant availableUntil;

    @Column(name = "shuffle_questions", nullable = false)
    @Builder.Default
    private boolean shuffleQuestions = true;

    @Column(name = "show_result_immediately", nullable = false)
    @Builder.Default
    private boolean showResultImmediately = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean mandatory = true;

    /**
     * Secure test mode: the student's browser reports leaving the test window, copying and the like.
     * See {@link #maxViolations}.
     */
    @Column(name = "secure_mode", nullable = false)
    private boolean secureMode;

    /** The counted violation that ends the attempt. 2 means one warning, then termination. */
    @Column(name = "max_violations", nullable = false)
    @Builder.Default
    private int maxViolations = 2;

    /**
     * The student's camera must be on: they allow it before starting, and the browser reports when no face
     * (or several) is visible. Independent of secure mode. Events are recorded and warn; none ends the attempt.
     */
    @Column(name = "require_camera", nullable = false)
    private boolean requireCamera;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private QuizStatus status = QuizStatus.DRAFT;

    @Column(name = "trainer_id")
    private Long trainerId;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** Whether the test is open right now, quite apart from who is asking. */
    public boolean isOpenAt(Instant at) {
        return status.isOpenToStudents()
                && (availableFrom == null || !at.isBefore(availableFrom))
                && (availableUntil == null || !at.isAfter(availableUntil));
    }

    public boolean passed(int percentage) {
        return percentage >= passPercentage;
    }

    public void publish(Instant at) {
        this.status = QuizStatus.PUBLISHED;
        this.publishedAt = at;
    }
}
