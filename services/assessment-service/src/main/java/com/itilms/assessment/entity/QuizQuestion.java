package com.itilms.assessment.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.itilms.common.entity.AuditableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One question on a test, with its options. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "quiz_questions")
public class QuizQuestion extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private QuestionType type = QuestionType.SINGLE_CHOICE;

    @Column(nullable = false)
    @Builder.Default
    private int marks = 1;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    /** Shown with the result, never while the student is answering. */
    @Column(columnDefinition = "text")
    private String explanation;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "question_id", nullable = false)
    @OrderBy("sequenceNo ASC")
    @Builder.Default
    private List<QuizOption> options = new ArrayList<>();

    public Set<Long> correctOptionIds() {
        return options.stream().filter(QuizOption::isCorrect)
                .map(QuizOption::getId).collect(Collectors.toSet());
    }

    /**
     * Marks awarded for a set of chosen options: all or nothing.
     *
     * <p>A multi-choice question scores only when the selection matches the key
     * exactly. Partial credit would need a rule the institute has not set - is
     * three of four right worth three quarters, or nothing? - and inventing one
     * here would decide it silently for every test.
     */
    public int scoreFor(Set<Long> selectedOptionIds) {
        if (selectedOptionIds == null || selectedOptionIds.isEmpty()) {
            return 0;
        }
        if (!type.allowsMultipleSelections() && selectedOptionIds.size() > 1) {
            return 0;
        }
        return correctOptionIds().equals(selectedOptionIds) ? marks : 0;
    }

    /** True when every option belongs to this question - the guard against a forged answer. */
    public boolean owns(Set<Long> optionIds) {
        Set<Long> mine = options.stream().map(QuizOption::getId).collect(Collectors.toSet());
        return mine.containsAll(optionIds);
    }

    public void addOption(QuizOption option) {
        this.options.add(option);
    }
}
