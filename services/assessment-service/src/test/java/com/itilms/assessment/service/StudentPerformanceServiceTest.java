package com.itilms.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.itilms.assessment.dto.response.StudentPerformanceResponse;
import com.itilms.assessment.entity.Assignment;
import com.itilms.assessment.entity.AssignmentStatus;
import com.itilms.assessment.entity.AttemptStatus;
import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizAnswer;
import com.itilms.assessment.entity.QuizAttempt;
import com.itilms.assessment.entity.QuizQuestion;
import com.itilms.assessment.entity.QuizStatus;
import com.itilms.assessment.entity.Submission;
import com.itilms.assessment.entity.SubmissionStatus;
import com.itilms.assessment.repository.AssignmentRepository;
import com.itilms.assessment.repository.QuizAnswerRepository;
import com.itilms.assessment.repository.QuizAttemptRepository;
import com.itilms.assessment.repository.QuizQuestionRepository;
import com.itilms.assessment.repository.QuizRepository;
import com.itilms.assessment.repository.SubmissionRepository;

/** The numbers on a student's progress report, and the rule that a student's copy does not reveal held-back results. */
class StudentPerformanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final long STUDENT = 10L;

    private QuizRepository quizzes;
    private QuizAttemptRepository attempts;
    private QuizAnswerRepository answers;
    private QuizQuestionRepository questions;
    private AssignmentRepository assignments;
    private SubmissionRepository submissions;
    private StudentPerformanceService service;

    @BeforeEach
    void setUp() {
        quizzes = mock(QuizRepository.class);
        attempts = mock(QuizAttemptRepository.class);
        answers = mock(QuizAnswerRepository.class);
        questions = mock(QuizQuestionRepository.class);
        assignments = mock(AssignmentRepository.class);
        submissions = mock(SubmissionRepository.class);
        service = new StudentPerformanceService(quizzes, attempts, answers, questions, assignments, submissions);

        when(attempts.findByStudentIdOrderByStartedAtDesc(STUDENT)).thenReturn(List.of());
        when(quizzes.findAllById(any())).thenReturn(List.of());
        when(quizzes.findPublishedFor(any(), any())).thenReturn(List.of());
        when(assignments.findByBatchIdInAndStatusInOrderByDueAtAsc(any(), any())).thenReturn(List.of());
    }

    private static Quiz quiz(long id, String title, int pass, boolean showResult) {
        return Quiz.builder().id(id).courseId(1L).title(title).passPercentage(pass).showResultImmediately(showResult)
                .status(QuizStatus.PUBLISHED).durationMinutes(30).build();
    }

    private static QuizAttempt attempt(long id, long quizId, int percentage, AttemptStatus status) {
        return QuizAttempt.builder().id(id).quizId(quizId).studentId(STUDENT).attemptNo(1).expiresAt(NOW)
                .percentage(percentage).score(percentage).status(status).build();
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("Tests: attempted, passed, the average of the best result on each, and which are below the pass mark")
    void testNumbers() {
        when(attempts.findByStudentIdOrderByStartedAtDesc(STUDENT)).thenReturn(List.of(
                attempt(1, 100, 60, AttemptStatus.SUBMITTED), attempt(2, 100, 80, AttemptStatus.SUBMITTED),   // best 80
                attempt(3, 200, 30, AttemptStatus.EXPIRED),                                                     // fails 50
                attempt(4, 300, 90, AttemptStatus.IN_PROGRESS)));                                               // not finished
        when(quizzes.findAllById(any())).thenReturn(List.of(quiz(100, "Java basics", 40, true), quiz(200, "OOP", 50, true)));

        var tests = service.performanceOf(STUDENT, List.of(7L), List.of(1L), false, NOW).tests();

        assertThat(tests.attempted()).isEqualTo(2);
        assertThat(tests.passed()).isEqualTo(1);
        assertThat(tests.averagePercent()).isEqualTo(55);       // (80 + 30) / 2
        assertThat(tests.bestPercent()).isEqualTo(80);
        assertThat(tests.below()).singleElement().satisfies(w -> {
            assertThat(w.title()).isEqualTo("OOP");
            assertThat(w.percent()).isEqualTo(30);
            assertThat(w.passPercent()).isEqualTo(50);
        });
    }

    @Test
    @DisplayName("A terminated attempt is counted, so the report can point at it")
    void terminatedCounted() {
        when(attempts.findByStudentIdOrderByStartedAtDesc(STUDENT)).thenReturn(List.of(
                attempt(1, 100, 70, AttemptStatus.TERMINATED)));
        when(quizzes.findAllById(any())).thenReturn(List.of(quiz(100, "Java basics", 40, true)));

        assertThat(service.performanceOf(STUDENT, List.of(7L), List.of(1L), false, NOW).tests().terminated()).isEqualTo(1);
    }

    @Test
    @DisplayName("A test whose results the trainer holds back is 'result pending' in the student's copy, and scored in staff's")
    void heldBackResultsStayHiddenFromTheStudent() {
        when(attempts.findByStudentIdOrderByStartedAtDesc(STUDENT)).thenReturn(List.of(
                attempt(1, 100, 90, AttemptStatus.SUBMITTED), attempt(2, 200, 20, AttemptStatus.SUBMITTED)));
        when(quizzes.findAllById(any())).thenReturn(List.of(quiz(100, "Open", 40, true), quiz(200, "Held back", 40, false)));

        var mine = service.performanceOf(STUDENT, List.of(7L), List.of(1L), true, NOW).tests();
        assertThat(mine.resultsPending()).isEqualTo(1);
        assertThat(mine.averagePercent()).isEqualTo(90);         // the held-back 20 is not in it
        assertThat(mine.below()).isEmpty();                       // ...and does not show up as a weak test
        assertThat(mine.attempted()).isEqualTo(2);

        var staff = service.performanceOf(STUDENT, List.of(7L), List.of(1L), false, NOW).tests();
        assertThat(staff.resultsPending()).isZero();
        assertThat(staff.averagePercent()).isEqualTo(55);
        assertThat(staff.below()).extracting(w -> w.title()).containsExactly("Held back");
    }

    @Test
    @DisplayName("Pending tests are the open ones for the student's batches that they have not taken")
    void pendingTests() {
        Quiz taken = quiz(100, "Taken", 40, true);
        Quiz open = quiz(101, "Open now", 40, true);
        Quiz closedWindow = quiz(102, "Window over", 40, true);
        closedWindow.setAvailableUntil(NOW.minusSeconds(3600));
        Quiz notYet = quiz(103, "Not yet open", 40, true);
        notYet.setAvailableFrom(NOW.plusSeconds(3600));
        when(attempts.findByStudentIdOrderByStartedAtDesc(STUDENT)).thenReturn(List.of(attempt(1, 100, 70, AttemptStatus.SUBMITTED)));
        when(quizzes.findAllById(any())).thenReturn(List.of(taken));
        when(quizzes.findPublishedFor(List.of(7L), List.of(1L))).thenReturn(List.of(taken, open, closedWindow, notYet));

        var pending = service.performanceOf(STUDENT, List.of(7L), List.of(1L), true, NOW).tests().pending();

        assertThat(pending).extracting(p -> p.title()).containsExactly("Open now");
    }

    @Test
    @DisplayName("A student in no batch has no pending tests, and the repository is not asked")
    void noBatchesNoPending() {
        var tests = service.performanceOf(STUDENT, List.of(), List.of(), true, NOW).tests();

        assertThat(tests.pending()).isEmpty();
        assertThat(tests.attempted()).isZero();
        assertThat(tests.averagePercent()).isNull();
        verify(quizzes, never()).findPublishedFor(any(), any());
    }

    // ------------------------------------------------------------------ coding

    @Test
    @DisplayName("Coding: the best attempt at each question counts, and the questions not fully solved are listed worst first")
    void codingNumbers() {
        when(attempts.findByStudentIdOrderByStartedAtDesc(STUDENT)).thenReturn(List.of(
                attempt(1, 100, 50, AttemptStatus.SUBMITTED), attempt(2, 101, 50, AttemptStatus.SUBMITTED)));
        when(answers.findByAttemptIdIn(any())).thenReturn(List.of(
                QuizAnswer.builder().attemptId(1L).questionId(5L).testsPassed(2).testsTotal(4).build(),
                QuizAnswer.builder().attemptId(2L).questionId(5L).testsPassed(3).testsTotal(4).build(),   // the better try at question 5
                QuizAnswer.builder().attemptId(1L).questionId(6L).testsPassed(4).testsTotal(4).build(),
                QuizAnswer.builder().attemptId(1L).questionId(7L).testsPassed(0).testsTotal(2).build(),
                QuizAnswer.builder().attemptId(1L).questionId(8L).build()));                                // a choice question: no tests
        when(questions.findAllById(any())).thenReturn(List.of(
                QuizQuestion.builder().id(5L).questionText("Reverse a string").build(),
                QuizQuestion.builder().id(6L).questionText("Sum two numbers").build(),
                QuizQuestion.builder().id(7L).questionText("x".repeat(200)).build()));

        var coding = service.performanceOf(STUDENT, List.of(7L), List.of(1L), false, NOW).coding();

        assertThat(coding.questionsAttempted()).isEqualTo(3);
        assertThat(coding.testCasesPassed()).isEqualTo(7);       // 3 + 4 + 0
        assertThat(coding.testCasesTotal()).isEqualTo(10);
        assertThat(coding.averagePercent()).isEqualTo(58);       // mean of 75, 100, 0
        assertThat(coding.lowest()).extracting(w -> w.questionId()).containsExactly(7L, 5L);
        assertThat(coding.lowest().get(0).question()).hasSize(80).endsWith("...");
    }

    @Test
    @DisplayName("Coding with nothing attempted is empty, not zero")
    void noCoding() {
        var coding = service.performanceOf(STUDENT, List.of(7L), List.of(1L), false, NOW).coding();

        assertThat(coding.questionsAttempted()).isZero();
        assertThat(coding.averagePercent()).isNull();
        assertThat(coding.lowest()).isEmpty();
    }

    // ------------------------------------------------------------------ assignments

    private static Assignment assignment(long id, String title, AssignmentStatus status, Instant due, int max) {
        return Assignment.builder().id(id).batchId(7L).title(title).status(status).dueAt(due).maxMarks(max).build();
    }

    @Test
    @DisplayName("Assignments: submitted, marked, the average mark, work sent back, late work, and what is still to hand in")
    void assignmentNumbers() {
        Instant yesterday = NOW.minusSeconds(86_400);
        Instant nextWeek = NOW.plusSeconds(7 * 86_400);
        when(assignments.findByBatchIdInAndStatusInOrderByDueAtAsc(any(), any())).thenReturn(List.of(
                assignment(1, "Marked", AssignmentStatus.PUBLISHED, yesterday, 10),
                assignment(2, "Missed", AssignmentStatus.PUBLISHED, yesterday, 10),
                assignment(3, "Closed and missed", AssignmentStatus.CLOSED, yesterday, 10),
                assignment(4, "Sent back", AssignmentStatus.PUBLISHED, nextWeek, 10),
                assignment(5, "Upcoming", AssignmentStatus.PUBLISHED, nextWeek, 10)));
        when(submissions.findByStudentIdAndAssignmentIdIn(any(), any())).thenReturn(List.of(
                Submission.builder().assignmentId(1L).studentId(STUDENT).status(SubmissionStatus.EVALUATED).marks(8)
                        .submittedAt(yesterday.plusSeconds(600)).build(),                                          // late, and marked 80%
                Submission.builder().assignmentId(4L).studentId(STUDENT).status(SubmissionStatus.RETURNED)
                        .submittedAt(NOW.minusSeconds(60)).build()));

        var work = service.performanceOf(STUDENT, List.of(7L), List.of(1L), false, NOW).assignments();

        assertThat(work.assigned()).isEqualTo(5);
        assertThat(work.submitted()).isEqualTo(2);
        assertThat(work.evaluated()).isEqualTo(1);
        assertThat(work.averagePercent()).isEqualTo(80);
        assertThat(work.returned()).isEqualTo(1);
        assertThat(work.late()).isEqualTo(1);
        // Still to hand in: the missed one (overdue) and the upcoming one; a closed assignment cannot be handed in any more.
        assertThat(work.pending()).extracting(p -> p.title() + (p.overdue() ? "!" : "")).containsExactly("Missed!", "Upcoming");
    }

    @Test
    @DisplayName("No batches, no assignments")
    void noAssignments() {
        var work = service.performanceOf(STUDENT, List.of(), List.of(), false, NOW).assignments();

        assertThat(work).isEqualTo(new StudentPerformanceResponse.Assignments(0, 0, 0, null, 0, 0, List.of()));
        verify(assignments, never()).findByBatchIdInAndStatusInOrderByDueAtAsc(any(), any());
    }
}
