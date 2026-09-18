package com.itilms.placement.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import com.itilms.common.entity.AuditableEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A job opening and who may apply for it (Doc S6.14, S7.4). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "job_openings")
public class JobOpening extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 20)
    @Builder.Default
    private JobType jobType = JobType.FULL_TIME;

    @Column(length = 160)
    private String location;

    @Column(name = "package_offered", length = 100)
    private String packageOffered;

    private Integer openings;

    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    /** Courses that qualify. Empty means any course. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "job_eligible_courses", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "course_id", nullable = false)
    @Builder.Default
    private Set<Long> eligibleCourseIds = new LinkedHashSet<>();

    @Column(name = "require_certificate", nullable = false)
    @Builder.Default
    private boolean requireCertificate = false;

    @Column(name = "min_attendance_percent")
    private Integer minAttendancePercent;

    @Column(name = "min_score_percent")
    private Integer minScorePercent;

    @Column(name = "eligibility_notes", columnDefinition = "text")
    private String eligibilityNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** Open, and the deadline (if any) has not passed. */
    public boolean acceptsApplicationsOn(LocalDate today) {
        return status == JobStatus.OPEN && (applicationDeadline == null || !today.isAfter(applicationDeadline));
    }

    public void publish(Instant at) {
        this.status = JobStatus.OPEN;
        this.publishedAt = at;
    }
}
