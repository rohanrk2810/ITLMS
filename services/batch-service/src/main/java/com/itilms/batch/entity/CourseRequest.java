package com.itilms.batch.entity;

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

/**
 * A student's request to join a course, waiting for an administrator or coordinator.
 *
 * <p>Student and course details are copied in when the request is made, so the reviewer
 * sees what the student saw. Approving it creates an {@link Enrollment}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "course_requests")
public class CourseRequest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "student_code", length = 30)
    private String studentCode;

    @Column(name = "student_name", length = 160)
    private String studentName;

    @Column(name = "student_email", length = 160)
    private String studentEmail;

    @Column(name = "student_phone", length = 30)
    private String studentPhone;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "course_code", length = 40)
    private String courseCode;

    @Column(name = "course_title", length = 200)
    private String courseTitle;

    @Column(name = "preferred_batch_id")
    private Long preferredBatchId;

    @Column(length = 1000)
    private String message;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CourseRequestStatus status = CourseRequestStatus.PENDING;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_by_name", length = 160)
    private String decidedByName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "approved_batch_id")
    private Long approvedBatchId;

    @Column(name = "enrollment_id")
    private Long enrollmentId;
}
