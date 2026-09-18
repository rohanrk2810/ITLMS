package com.itilms.assessment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The rules that turn answers into marks and decide whether work is accepted.
 *
 * <p>Each case is one a student or trainer could reasonably dispute.
 */
class ScoringRulesTest {

    private static QuizQuestion question(QuestionType type, int marks, boolean... correct) {
        QuizQuestion q = QuizQuestion.builder().id(1L).quizId(1L).type(type).marks(marks)
                .sequenceNo(1).questionText("?").build();
        for (int i = 0; i < correct.length; i++) {
            q.addOption(QuizOption.builder().id(10L + i).optionText("o" + i)
                    .correct(correct[i]).sequenceNo(i + 1).build());
        }
        return q;
    }

    @Nested
    @DisplayName("Marking one question")
    class Questions {

        private final QuizQuestion single = question(QuestionType.SINGLE_CHOICE, 2, false, true, false, false);
        private final QuizQuestion multi = question(QuestionType.MULTI_CHOICE, 3, true, false, true, false);

        @Test
        @DisplayName("The right option scores full marks, a wrong one scores nothing")
        void singleChoice() {
            assertThat(single.scoreFor(Set.of(11L))).isEqualTo(2);
            assertThat(single.scoreFor(Set.of(10L))).isZero();
        }

        @Test
        @DisplayName("Ticking every option on a single-choice question scores nothing")
        void cannotHedge() {
            assertThat(single.scoreFor(Set.of(10L, 11L, 12L, 13L))).isZero();
        }

        @Test
        @DisplayName("Multi-choice is all or nothing: the exact set of right options")
        void multiChoice() {
            assertThat(multi.scoreFor(Set.of(10L, 12L))).isEqualTo(3);
            assertThat(multi.scoreFor(Set.of(10L))).as("half right").isZero();
            assertThat(multi.scoreFor(Set.of(10L, 12L, 13L))).as("right plus a wrong one").isZero();
        }

        @Test
        @DisplayName("An unanswered question scores nothing and is not an error")
        void unanswered() {
            assertThat(single.scoreFor(Set.of())).isZero();
            assertThat(single.scoreFor(null)).isZero();
        }

        @Test
        @DisplayName("Options from another question are recognised as foreign")
        void ownership() {
            assertThat(single.owns(Set.of(10L, 13L))).isTrue();
            assertThat(single.owns(Set.of(10L, 99L))).isFalse();
        }
    }

    @Nested
    @DisplayName("Percentage and pass mark")
    class Percentages {

        private QuizAttempt scored(int score, int total, int passMark) {
            QuizAttempt attempt = QuizAttempt.builder().quizId(1L).studentId(1L).attemptNo(1)
                    .expiresAt(Instant.now()).build();
            attempt.complete(score, total, passMark, Instant.now(), false);
            return attempt;
        }

        @Test
        @DisplayName("69.9% does not pass a 70% test")
        void roundsDown() {
            QuizAttempt attempt = scored(699, 1000, 70);
            assertThat(attempt.getPercentage()).isEqualTo(69);
            assertThat(attempt.getPassed()).isFalse();
        }

        @Test
        @DisplayName("Exactly the pass mark passes")
        void inclusive() {
            assertThat(scored(7, 10, 70).getPassed()).isTrue();
        }

        @Test
        @DisplayName("A test with no marks available scores 0% rather than failing")
        void emptyTest() {
            assertThat(scored(0, 0, 40).getPercentage()).isZero();
        }

        @Test
        @DisplayName("An attempt that ran out of time is recorded as EXPIRED, with its score")
        void expired() {
            QuizAttempt attempt = QuizAttempt.builder().quizId(1L).studentId(1L).attemptNo(1)
                    .expiresAt(Instant.now()).build();
            attempt.complete(4, 10, 40, Instant.now(), true);
            assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
            assertThat(attempt.getScore()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Accepting assignment submissions")
    class Submissions {

        private final Instant due = Instant.parse("2026-01-12T12:00:00Z");

        private Assignment assignment(AssignmentStatus status, boolean allowLate) {
            return Assignment.builder().batchId(1L).title("t").dueAt(due).maxMarks(10)
                    .status(status).allowLate(allowLate).build();
        }

        @Test
        @DisplayName("Late work is accepted by default, to be marked LATE")
        void lateAccepted() {
            Assignment a = assignment(AssignmentStatus.PUBLISHED, true);
            assertThat(a.acceptsSubmissionAt(due.plus(Duration.ofDays(2)))).isTrue();
            assertThat(a.isOverdue(due.plus(Duration.ofSeconds(1)))).isTrue();
        }

        @Test
        @DisplayName("Late work is refused when the trainer turned that off")
        void lateRefused() {
            Assignment a = assignment(AssignmentStatus.PUBLISHED, false);
            assertThat(a.acceptsSubmissionAt(due.minusSeconds(1))).isTrue();
            assertThat(a.acceptsSubmissionAt(due.plusSeconds(1))).isFalse();
        }

        @Test
        @DisplayName("Draft and closed assignments accept nothing")
        void notOpen() {
            assertThat(assignment(AssignmentStatus.DRAFT, true).acceptsSubmissionAt(due.minusSeconds(60))).isFalse();
            assertThat(assignment(AssignmentStatus.CLOSED, true).acceptsSubmissionAt(due.minusSeconds(60))).isFalse();
        }

        @Test
        @DisplayName("Resubmitting clears the earlier mark: it described different work")
        void resubmitClearsMark() {
            Submission s = Submission.builder().assignmentId(1L).studentId(1L).textAnswer("v1").build();
            s.returnForRework("redo part 2", 5L, due);
            s.resubmit("v2", due.minusSeconds(10), false);

            assertThat(s.getSubmissionCount()).isEqualTo(2);
            assertThat(s.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
            assertThat(s.getFeedback()).isNull();
            assertThat(s.getEvaluatedBy()).isNull();
        }

        @Test
        @DisplayName("Marked work cannot be replaced by the student")
        void markedIsFinal() {
            Submission s = Submission.builder().assignmentId(1L).studentId(1L).build();
            s.evaluate(8, "good", 5L, due);
            assertThat(s.getStatus().allowsResubmission()).isFalse();
        }
    }
}
