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

/** A piece of work set for a batch (Doc S6.10). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "assignments")
public class Assignment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String instructions;

    /** Handle of the brief or starter files in file-service. */
    @Column(name = "attachment_ref", length = 120)
    private String attachmentRef;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "max_marks", nullable = false)
    private int maxMarks;

    @Column(name = "allow_late", nullable = false)
    @Builder.Default
    private boolean allowLate = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean mandatory = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    @Column(name = "trainer_id")
    private Long trainerId;

    @Column(name = "published_at")
    private Instant publishedAt;

    public boolean isOverdue(Instant at) {
        return at.isAfter(dueAt);
    }

    /**
     * Whether work can still be handed in.
     *
     * <p>Being past the deadline is not on its own a refusal: a late
     * submission is normally accepted and marked LATE (Doc S14). It is refused
     * only when the trainer turned that off for this assignment.
     */
    public boolean acceptsSubmissionAt(Instant at) {
        return status.acceptsSubmissions() && (allowLate || !isOverdue(at));
    }

    public void publish(Instant at) {
        this.status = AssignmentStatus.PUBLISHED;
        this.publishedAt = at;
    }
}
