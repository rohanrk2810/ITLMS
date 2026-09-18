package com.itilms.assessment.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.itilms.common.entity.AuditableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One student's answer to one assignment.
 *
 * <p>Replaced in place when they hand in again, rather than stacked as new
 * rows: the trainer marks the latest version, and {@code submissionCount}
 * records that it changed.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "assignment_submissions")
public class Submission extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "student_name", length = 160)
    private String studentName;

    @Column(name = "student_user_id")
    private Long studentUserId;

    @Column(name = "text_answer", columnDefinition = "text")
    private String textAnswer;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default
    private Instant submittedAt = Instant.now();

    @Column(name = "submission_count", nullable = false)
    @Builder.Default
    private int submissionCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    private Integer marks;

    @Column(columnDefinition = "text")
    private String feedback;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    @Column(name = "evaluated_by")
    private Long evaluatedBy;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "submission_id")
    @OrderBy("uploadedAt ASC")
    @Builder.Default
    private List<SubmissionFile> files = new ArrayList<>();

    /** Replaces the work with a newer version, keeping the trainer's history intact. */
    public void resubmit(String text, Instant at, boolean late) {
        this.textAnswer = text;
        this.submittedAt = at;
        this.submissionCount++;
        this.status = late ? SubmissionStatus.LATE : SubmissionStatus.SUBMITTED;
        // A resubmission is new work, so an earlier mark no longer applies.
        this.marks = null;
        this.feedback = null;
        this.evaluatedAt = null;
        this.evaluatedBy = null;
        this.files.clear();
    }

    public void evaluate(int marks, String feedback, Long trainerUserId, Instant at) {
        this.marks = marks;
        this.feedback = feedback;
        this.evaluatedBy = trainerUserId;
        this.evaluatedAt = at;
        this.status = SubmissionStatus.EVALUATED;
    }

    public void returnForRework(String feedback, Long trainerUserId, Instant at) {
        this.feedback = feedback;
        this.evaluatedBy = trainerUserId;
        this.evaluatedAt = at;
        this.status = SubmissionStatus.RETURNED;
    }

    public void addFile(SubmissionFile file) {
        this.files.add(file);
    }
}
