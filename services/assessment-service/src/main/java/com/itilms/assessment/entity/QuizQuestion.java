package com.itilms.assessment.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.itilms.common.code.CodeLanguage;
import com.itilms.common.entity.AuditableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
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

    /** CODING only: the language the student writes in. */
    @Enumerated(EnumType.STRING)
    @Column(name = "code_language", length = 10)
    private CodeLanguage codeLanguage;

    /** CODING only: what the editor starts with. Shown to the student. */
    @Column(name = "starter_code", columnDefinition = "text")
    private String starterCode;

    /** SHORT_ANSWER only: any of these, compared after {@link #normalise}, earns the marks. The key: never sent to a student. */
    @ElementCollection
    @CollectionTable(name = "quiz_accepted_answers", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "sequence_no")
    @Column(name = "answer_text", nullable = false, length = 500)
    @Builder.Default
    private List<String> acceptedAnswers = new ArrayList<>();

    /** CODING only. */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "question_id", nullable = false)
    @OrderBy("sequenceNo ASC")
    @Builder.Default
    private List<QuizTestCase> testCases = new ArrayList<>();

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
        if (!type.isChoice() || selectedOptionIds == null || selectedOptionIds.isEmpty()) {
            return 0;
        }
        if (!type.allowsMultipleSelections() && selectedOptionIds.size() > 1) {
            return 0;
        }
        return correctOptionIds().equals(selectedOptionIds) ? marks : 0;
    }

    /**
     * Marks for a typed answer: all or nothing, if it equals an accepted answer once both are trimmed,
     * have their runs of spaces collapsed and are lower-cased. Nothing cleverer (spelling, synonyms) is
     * attempted; a question that needs more than that should be a choice question.
     */
    public int scoreText(String answerText) {
        if (type != QuestionType.SHORT_ANSWER || answerText == null || answerText.isBlank()) {
            return 0;
        }
        String given = normalise(answerText);
        return acceptedAnswers.stream().map(QuizQuestion::normalise).anyMatch(given::equals) ? marks : 0;
    }

    public static String normalise(String text) {
        return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Sum of the weights of every test case: the denominator of a coding score. */
    public int totalTestWeight() {
        return testCases.stream().mapToInt(QuizTestCase::getWeight).sum();
    }

    /** The share of this question's marks earned by passing test cases worth {@code passedWeight} of the total. */
    public int codingMarks(int passedWeight) {
        int total = totalTestWeight();
        return total == 0 ? 0 : (int) Math.round((double) marks * passedWeight / total);
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
