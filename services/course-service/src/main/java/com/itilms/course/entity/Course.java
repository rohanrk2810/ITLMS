package com.itilms.course.entity;

import java.math.BigDecimal;
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
 * A course in the catalog (Doc S6.5).
 *
 * <p>The fee here is the list price. What a particular student actually pays is
 * negotiated at admission and lives in finance-service — a discount agreed with
 * one student must never change what the catalog advertises to everyone else.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "courses")
public class Course extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String title;

    /** Short code used in batch codes, e.g. JFS for "Java Full Stack". */
    @Column(nullable = false, length = 30)
    private String code;

    /** One-paragraph pitch for the catalog card. */
    @Column(length = 500)
    private String summary;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "learning_outcomes", columnDefinition = "text")
    private String learningOutcomes;

    @Column(columnDefinition = "text")
    private String prerequisites;

    @Column(name = "technology_stack", length = 400)
    private String technologyStack;

    @Column(name = "duration_hours", nullable = false)
    @Builder.Default
    private Integer durationHours = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CourseLevel level = CourseLevel.BEGINNER;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal fee = BigDecimal.ZERO;

    /** file-service handle for the catalog image. */
    @Column(name = "thumbnail_ref", length = 64)
    private String thumbnailRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CourseStatus status = CourseStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    public void publish() {
        this.status = CourseStatus.PUBLISHED;
        if (this.publishedAt == null) {
            this.publishedAt = Instant.now();
        }
    }

    /**
     * Withdraws a course from the catalog.
     *
     * <p>{@code publishedAt} is kept: it records when the course was first
     * offered, which reports about past intakes still need.
     */
    public void archive() {
        this.status = CourseStatus.ARCHIVED;
    }
}
