package com.itilms.liveclass.entity;

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
 * A question prepared ahead of a class. {@code payload} is the whole ask-a-question request as JSON, so sending it to
 * the room is exactly the same as the trainer typing it live.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "live_materials")
public class LiveMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false)
    private String payload;

    @Column(name = "course_id")
    private Long courseId;

    private String topic;

    /** Comma-separated, lower-case. */
    private String tags;

    /** EASY, MEDIUM or HARD. */
    private String difficulty;

    /** Other trainers can see and copy a shared item; only its owner can change it. */
    @Builder.Default
    @Column(nullable = false)
    private boolean shared = false;

    /** Slash-separated path such as Java/OOPs/Coding; null is the top level. */
    private String folder;

    @Column(name = "owner_name")
    private String ownerName;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
