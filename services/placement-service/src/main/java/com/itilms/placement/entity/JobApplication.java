package com.itilms.placement.entity;

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

/** A student's application for one job, and how far it has got. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "job_applications")
public class JobApplication extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "student_user_id")
    private Long studentUserId;

    @Column(name = "student_name", length = 160)
    private String studentName;

    /** The course the student qualified through. */
    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "resume_ref", length = 120)
    private String resumeRef;

    @Column(name = "cover_note", columnDefinition = "text")
    private String coverNote;

    @Column(name = "applied_at", nullable = false)
    @Builder.Default
    private Instant appliedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ApplicationStage stage = ApplicationStage.APPLIED;

    @Column(name = "current_round")
    private Integer currentRound;

    @Column(name = "next_interview_at")
    private Instant nextInterviewAt;

    @Column(name = "offer_details", length = 255)
    private String offerDetails;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
