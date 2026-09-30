package com.itilms.liveclass.entity;

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
 * A question asked during a live class. {@code offsetSeconds} is how far into the class it was asked, which is what
 * lets a recording of the class offer it at the same moment.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "live_questions")
public class LiveQuestion {

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "live_session_id", nullable = false)
    private Long liveSessionId;

    @Column(name = "class_session_id", nullable = false)
    private Long classSessionId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "asked_by_user_id", nullable = false)
    private Long askedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;

    @Column(nullable = false)
    private String prompt;

    /** JSON array of option texts. */
    private String options;

    /** JSON array of the indexes of the right options. */
    @Column(name = "correct_options")
    private String correctOptions;

    /** JSON array of accepted short answers. Empty or absent means the trainer marks it. */
    @Column(name = "accepted_answers")
    private String acceptedAnswers;

    private String language;

    @Column(name = "starter_code")
    private String starterCode;

    private String explanation;

    @Builder.Default
    @Column(nullable = false)
    private int marks = 1;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String status = OPEN;

    @Column(name = "asked_at", nullable = false)
    private Instant askedAt;

    @Column(name = "offset_seconds", nullable = false)
    private int offsetSeconds;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public boolean isOpen() {
        return OPEN.equals(status);
    }
}
