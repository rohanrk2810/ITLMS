package com.itilms.assessment.entity;

import java.time.Duration;
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

/** One sitting of a test by one student. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "student_name", length = 160)
    private String studentName;

    @Column(name = "student_user_id")
    private Long studentUserId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    /**
     * When this sitting runs out, fixed at the moment it began.
     *
     * <p>The clock belongs to the server. A client-side timer is a display, not
     * a rule: closing the laptop and coming back an hour later must not hand
     * anyone an extra hour.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    private Integer score;

    private Integer percentage;

    private Boolean passed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    /** Violations that counted toward ending a secure test. */
    @Column(name = "violation_count", nullable = false)
    @Builder.Default
    private int violationCount = 0;

    @Column(name = "terminated_reason", length = 200)
    private String terminatedReason;

    public static Instant deadline(Instant startedAt, int durationMinutes) {
        return startedAt.plus(Duration.ofMinutes(durationMinutes));
    }

    public boolean hasExpired(Instant at) {
        return at.isAfter(expiresAt);
    }

    public int secondsRemaining(Instant at) {
        long seconds = Duration.between(at, expiresAt).toSeconds();
        return seconds > 0 ? (int) seconds : 0;
    }

    /**
     * Records the result.
     *
     * <p>An attempt whose time ran out is still scored on what was answered:
     * a connection that died with ten minutes left should not erase the work
     * already done.
     *
     * <p>The percentage is rounded down, so 69.9% does not pass a 70% test.
     * The pass mark means what it says, and it is judged the same way as live
     * class attendance.
     */
    public void complete(int score, int totalMarks, int passPercentage, Instant at, boolean expired) {
        this.score = score;
        this.percentage = percentOf(score, totalMarks);
        this.passed = this.percentage >= passPercentage;
        this.submittedAt = at;
        this.status = expired ? AttemptStatus.EXPIRED : AttemptStatus.SUBMITTED;
    }

    /**
     * Ends a scored attempt as terminated: whatever it scored stays on record, but it did not pass.
     * Call after {@link #complete}.
     */
    public void terminate(String reason) {
        this.status = AttemptStatus.TERMINATED;
        this.passed = false;
        this.terminatedReason = reason;
    }

    static int percentOf(int score, int totalMarks) {
        if (totalMarks <= 0 || score <= 0) {
            return 0;
        }
        return (int) Math.min(100, (long) score * 100 / totalMarks);
    }
}
