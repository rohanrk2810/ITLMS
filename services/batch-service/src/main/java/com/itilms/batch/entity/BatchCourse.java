package com.itilms.batch.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A course a batch holds besides the one it was created for ({@code batches.course_id}).
 *
 * <p>Each carries its own trainer. Every student placed in the whole batch has the course too, as an enrolment marked
 * {@code included}: they pay the batch fee and nothing more for it. The course names and the trainer name are copies,
 * for the same reason the batch row keeps them (a timetable must not call two services per row).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "batch_courses")
public class BatchCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "course_code", length = 30)
    private String courseCode;

    @Column(name = "course_title", length = 160)
    private String courseTitle;

    @Column(name = "trainer_id")
    private Long trainerId;

    @Column(name = "trainer_user_id")
    private Long trainerUserId;

    @Column(name = "trainer_name", length = 160)
    private String trainerName;

    @Column(name = "added_at", nullable = false)
    @Builder.Default
    private Instant addedAt = Instant.now();
}
