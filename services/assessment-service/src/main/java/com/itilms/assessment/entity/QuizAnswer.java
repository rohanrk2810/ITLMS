package com.itilms.assessment.entity;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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

/**
 * What a student chose for one question, and what it was worth.
 *
 * <p>Kept per question rather than only as a total so the result screen can
 * show which ones were wrong, and so a trainer can see the question the whole
 * batch missed. The marks here are the server's arithmetic; nothing the
 * browser sent contributes to them (Doc S14).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "quiz_answers")
public class QuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @ElementCollection
    @CollectionTable(name = "quiz_answer_options", joinColumns = @JoinColumn(name = "answer_id"))
    @Column(name = "option_id", nullable = false)
    @Builder.Default
    private Set<Long> selectedOptionIds = new LinkedHashSet<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean correct = false;

    @Column(name = "marks_awarded", nullable = false)
    @Builder.Default
    private int marksAwarded = 0;

    @Column(name = "answered_at", nullable = false)
    @Builder.Default
    private Instant answeredAt = Instant.now();
}
