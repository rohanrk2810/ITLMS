package com.itilms.assessment.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.assessment.dto.response.StudentPerformanceResponse;
import com.itilms.assessment.dto.response.StudentPerformanceResponse.Assignments;
import com.itilms.assessment.dto.response.StudentPerformanceResponse.Coding;
import com.itilms.assessment.dto.response.StudentPerformanceResponse.CodingWeak;
import com.itilms.assessment.dto.response.StudentPerformanceResponse.Pending;
import com.itilms.assessment.dto.response.StudentPerformanceResponse.Tests;
import com.itilms.assessment.dto.response.StudentPerformanceResponse.Weak;
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

import lombok.RequiredArgsConstructor;

/**
 * One student's performance on tests, coding questions and assignments, for their progress report.
 *
 * <p>Internal: it does not decide who may see this student's numbers (reporting-service does, before it asks),
 * but it does honour one privacy rule itself. When {@code forStudent} is set, a test whose trainer is still
 * holding results back is counted as "result pending" rather than scored, exactly as it is on the student's own
 * results page, so the report cannot leak what that page hides.
 */
@Service
@RequiredArgsConstructor
public class StudentPerformanceService {

    private static final int LIST_LIMIT = 5;
    /** JPQL "IN" with an empty collection is a portability trap; an id that cannot exist says "none". */
    private static final List<Long> NONE = List.of(-1L);

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAnswerRepository answerRepository;
    private final QuizQuestionRepository questionRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;

    @Transactional(readOnly = true)
    public StudentPerformanceResponse performanceOf(Long studentId, Collection<Long> batchIds,
                                                    Collection<Long> courseIds, boolean forStudent, Instant now) {
        List<QuizAttempt> finished = attemptRepository.findByStudentIdOrderByStartedAtDesc(studentId).stream()
                .filter(a -> a.getStatus().isFinished()).toList();
        return new StudentPerformanceResponse(
                tests(finished, batchIds, courseIds, forStudent, now),
                coding(finished),
                assignments(studentId, batchIds, now));
    }

    // -----------------------------------------------------------------

    private Tests tests(List<QuizAttempt> finished, Collection<Long> batchIds, Collection<Long> courseIds,
                        boolean forStudent, Instant now) {
        Map<Long, List<QuizAttempt>> byQuiz = finished.stream().collect(Collectors.groupingBy(QuizAttempt::getQuizId));
        Map<Long, Quiz> quizzes = quizRepository.findAllById(byQuiz.keySet()).stream()
                .collect(Collectors.toMap(Quiz::getId, Function.identity()));

        int passed = 0;
        int resultsPending = 0;
        List<Integer> bests = new ArrayList<>();
        List<Weak> below = new ArrayList<>();
        for (Map.Entry<Long, List<QuizAttempt>> entry : byQuiz.entrySet()) {
            Quiz quiz = quizzes.get(entry.getKey());
            if (quiz == null) {
                continue;
            }
            if (forStudent && !resultsVisible(quiz)) {
                resultsPending++;
                continue;
            }
            int best = entry.getValue().stream().map(QuizAttempt::getPercentage).filter(Objects::nonNull)
                    .max(Integer::compare).orElse(0);
            bests.add(best);
            if (quiz.passed(best)) {
                passed++;
            } else {
                below.add(new Weak(quiz.getId(), quiz.getTitle(), best, quiz.getPassPercentage()));
            }
        }
        below.sort(Comparator.comparingInt(Weak::percent));

        int terminated = (int) finished.stream().filter(a -> a.getStatus() == AttemptStatus.TERMINATED).count();

        List<Pending> pending = new ArrayList<>();
        if (!batchIds.isEmpty() || !courseIds.isEmpty()) {
            for (Quiz quiz : quizRepository.findPublishedFor(
                    batchIds.isEmpty() ? NONE : batchIds, courseIds.isEmpty() ? NONE : courseIds)) {
                if (quiz.isOpenAt(now) && !byQuiz.containsKey(quiz.getId())) {
                    pending.add(new Pending(quiz.getId(), quiz.getTitle(), quiz.getAvailableUntil(), false));
                }
            }
        }

        return new Tests(bests.size() + resultsPending, passed, average(bests),
                bests.stream().max(Integer::compare).orElse(null), terminated, resultsPending,
                pending.stream().limit(LIST_LIMIT).toList(), below.stream().limit(LIST_LIMIT).toList());
    }

    private Coding coding(List<QuizAttempt> finished) {
        if (finished.isEmpty()) {
            return new Coding(0, 0, 0, null, List.of());
        }
        // The best attempt at each question counts: someone who fixed their code on the second try is judged on that.
        Map<Long, int[]> best = new HashMap<>();
        for (QuizAnswer answer : answerRepository.findByAttemptIdIn(finished.stream().map(QuizAttempt::getId).toList())) {
            if (answer.getTestsTotal() == null || answer.getTestsTotal() <= 0) {
                continue;
            }
            int passed = answer.getTestsPassed() == null ? 0 : answer.getTestsPassed();
            int[] current = best.get(answer.getQuestionId());
            if (current == null || share(passed, answer.getTestsTotal()) > share(current[0], current[1])) {
                best.put(answer.getQuestionId(), new int[] {passed, answer.getTestsTotal()});
            }
        }
        if (best.isEmpty()) {
            return new Coding(0, 0, 0, null, List.of());
        }

        Map<Long, QuizQuestion> questions = questionRepository.findAllById(best.keySet()).stream()
                .collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));
        int casesPassed = best.values().stream().mapToInt(v -> v[0]).sum();
        int casesTotal = best.values().stream().mapToInt(v -> v[1]).sum();
        List<Integer> shares = best.values().stream().map(v -> (int) Math.round(share(v[0], v[1]) * 100)).toList();

        List<CodingWeak> lowest = best.entrySet().stream()
                .filter(e -> e.getValue()[0] < e.getValue()[1])
                .sorted(Comparator.comparingDouble(e -> share(e.getValue()[0], e.getValue()[1])))
                .limit(LIST_LIMIT)
                .map(e -> new CodingWeak(e.getKey(), shorten(questions.get(e.getKey())), e.getValue()[0], e.getValue()[1]))
                .toList();
        return new Coding(best.size(), casesPassed, casesTotal, average(shares), lowest);
    }

    private Assignments assignments(Long studentId, Collection<Long> batchIds, Instant now) {
        if (batchIds.isEmpty()) {
            return new Assignments(0, 0, 0, null, 0, 0, List.of());
        }
        List<Assignment> assigned = assignmentRepository.findByBatchIdInAndStatusInOrderByDueAtAsc(
                batchIds, List.of(AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED));
        if (assigned.isEmpty()) {
            return new Assignments(0, 0, 0, null, 0, 0, List.of());
        }
        Map<Long, Assignment> byId = assigned.stream().collect(Collectors.toMap(Assignment::getId, Function.identity()));
        Map<Long, Submission> submitted = submissionRepository
                .findByStudentIdAndAssignmentIdIn(studentId, byId.keySet()).stream()
                .collect(Collectors.toMap(Submission::getAssignmentId, Function.identity(), (a, b) -> a));

        int evaluated = 0;
        int returned = 0;
        int late = 0;
        List<Integer> percents = new ArrayList<>();
        for (Submission submission : submitted.values()) {
            Assignment assignment = byId.get(submission.getAssignmentId());
            if (submission.getStatus() == SubmissionStatus.EVALUATED) {
                evaluated++;
                if (submission.getMarks() != null && assignment.getMaxMarks() > 0) {
                    percents.add(submission.getMarks() * 100 / assignment.getMaxMarks());
                }
            }
            if (submission.getStatus() == SubmissionStatus.RETURNED) {
                returned++;
            }
            if (submission.getStatus() == SubmissionStatus.LATE
                    || assignment.getDueAt() != null && submission.getSubmittedAt().isAfter(assignment.getDueAt())) {
                late++;
            }
        }

        List<Pending> pending = assigned.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.PUBLISHED && !submitted.containsKey(a.getId()))
                .map(a -> new Pending(a.getId(), a.getTitle(), a.getDueAt(), a.getDueAt() != null && a.getDueAt().isBefore(now)))
                .limit(LIST_LIMIT).toList();
        return new Assignments(assigned.size(), submitted.size(), evaluated, average(percents), returned, late, pending);
    }

    // -----------------------------------------------------------------

    private static boolean resultsVisible(Quiz quiz) {
        return quiz.isShowResultImmediately() || quiz.getStatus() == QuizStatus.CLOSED;
    }

    private static Integer average(List<Integer> values) {
        return values.isEmpty() ? null : (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private static double share(int passed, int total) {
        return total <= 0 ? 0 : (double) passed / total;
    }

    private static String shorten(QuizQuestion question) {
        if (question == null || question.getQuestionText() == null) {
            return "A coding question";
        }
        String text = question.getQuestionText().strip().replaceAll("\\s+", " ");
        return text.length() <= 80 ? text : text.substring(0, 77) + "...";
    }
}
