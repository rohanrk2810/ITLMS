package com.itilms.notification.entity;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Local copy of an enrolment, kept current from batch-service events. Written only by upsert. */
@Getter
@NoArgsConstructor
@Entity
@IdClass(BatchMember.Key.class)
@Table(name = "batch_members")
public class BatchMember {

    @Id
    @Column(name = "batch_id")
    private Long batchId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @lombok.Data
    @NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Key implements Serializable {
        private Long batchId;
        private Long userId;
    }
}
