package com.itilms.assessment.entity;

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
 * One input and the output a correct program prints for it.
 *
 * <p>Like {@link QuizOption#isCorrect()}, {@code expectedOutput} is never sent to a student before the
 * result is released; a hidden case is not sent at all, only its pass or fail afterwards.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "quiz_test_cases")
public class QuizTestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private String input = "";

    @Column(name = "expected_output", nullable = false, columnDefinition = "text")
    private String expectedOutput;

    @Column(nullable = false)
    private boolean hidden;

    @Column(nullable = false)
    @Builder.Default
    private int weight = 1;
}
