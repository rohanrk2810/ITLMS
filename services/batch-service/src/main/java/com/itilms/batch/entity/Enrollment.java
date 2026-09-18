package com.itilms.batch.entity;

import java.time.Instant;
import java.time.LocalDate;

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

/**
 * One student's place in one batch.
 *
 * <p>The authoritative record of who is in a class. course-service, finance and
 * reporting all keep their own copies for their own queries, but they are copies
 * — this row is what they are copies of.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "enrollments")
public class Enrollment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** Carried so notifications can reach the person without a lookup. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "student_code", length = 30)
    private String studentCode;

    @Column(name = "student_name", length = 160)
    private String studentName;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "enrolled_at", nullable = false)
    @Builder.Default
    private Instant enrolledAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @Column(name = "dropped_reason", length = 255)
    private String droppedReason;

    public void complete() {
        this.status = EnrollmentStatus.COMPLETED;
        this.completionDate = LocalDate.now();
    }

    public void drop(String reason) {
        this.status = EnrollmentStatus.DROPPED;
        this.droppedReason = reason;
    }

    public void transfer(Long newBatchId) {
        this.status = EnrollmentStatus.TRANSFERRED;
        this.droppedReason = "Transferred to batch " + newBatchId;
    }
}
