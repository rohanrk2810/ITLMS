package com.itilms.batch.entity;

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
 * An additional trainer on a batch (Doc S6.6, "trainer(s)").
 *
 * <p>The primary trainer also lives on the batch row itself. That duplication is
 * intentional: almost every query wants the primary trainer and nothing else,
 * and making the common case a join for the sake of the uncommon one would slow
 * down every timetable and batch listing in the system.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "batch_trainers")
public class BatchTrainer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "trainer_id", nullable = false)
    private Long trainerId;

    @Column(name = "trainer_user_id")
    private Long trainerUserId;

    @Column(name = "trainer_name", length = 160)
    private String trainerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BatchTrainerRole role = BatchTrainerRole.CO_TRAINER;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
