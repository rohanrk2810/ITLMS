package com.itilms.course.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

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

/**
 * A local mirror of an enrolment owned by batch-service.
 *
 * <p>Progress is recorded against a lesson, and lessons belong to this service.
 * Keeping the enrolment's identity here means recording "lesson watched" is one
 * local write, not a cross-service round trip on an action students perform
 * dozens of times a session.
 *
 * <p>This service never creates these rows from a request — only from
 * {@code EnrollmentCreatedEvent}. batch-service decides who is enrolled.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "course_enrollments")
public class CourseEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The id batch-service assigned. Unique, and how events are matched. */
    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** Kept alongside studentId so notifications can address the person directly. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "batch_id")
    private Long batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal progressPercent = BigDecimal.ZERO;

    /**
     * Denormalised counters.
     *
     * <p>Recomputed whenever a lesson is completed, so the student dashboard
     * reads one row instead of counting progress rows for every enrolment on
     * the page.
     */
    @Column(name = "completed_lessons", nullable = false)
    @Builder.Default
    private Integer completedLessons = 0;

    @Column(name = "total_lessons", nullable = false)
    @Builder.Default
    private Integer totalLessons = 0;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "enrolled_at", nullable = false)
    @Builder.Default
    private Instant enrolledAt = Instant.now();

    /**
     * Recalculates the percentage from the two counters.
     *
     * <p>A course with no mandatory lessons yet reports 0%, not 100%. An empty
     * curriculum means nobody has built the course, and reporting every student
     * on it as finished would let certificates be issued against nothing.
     */
    public void recalculate(int completed, int total) {
        this.completedLessons = completed;
        this.totalLessons = total;

        if (total <= 0) {
            this.progressPercent = BigDecimal.ZERO;
            this.completedAt = null;
            return;
        }

        this.progressPercent = BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        if (completed >= total) {
            if (this.completedAt == null) {
                this.completedAt = Instant.now();
            }
        } else {
            // A lesson was un-completed, or new material was added to the
            // course. Either way this enrolment is no longer finished.
            this.completedAt = null;
        }
    }

    public boolean allLessonsComplete() {
        return totalLessons > 0 && completedLessons >= totalLessons;
    }
}
