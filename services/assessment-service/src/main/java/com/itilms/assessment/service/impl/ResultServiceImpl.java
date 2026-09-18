package com.itilms.assessment.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.assessment.dto.response.CompletionResponse;
import com.itilms.assessment.dto.response.MyResultsResponse;
import com.itilms.assessment.entity.Assignment;
import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizAttempt;
import com.itilms.assessment.entity.QuizStatus;
import com.itilms.assessment.entity.Submission;
import com.itilms.assessment.entity.SubmissionStatus;
import com.itilms.assessment.repository.AssignmentRepository;
import com.itilms.assessment.repository.QuizAttemptRepository;
import com.itilms.assessment.repository.QuizRepository;
import com.itilms.assessment.repository.SubmissionRepository;
import com.itilms.assessment.service.AssessmentAccess;
import com.itilms.assessment.service.ResultService;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResultServiceImpl implements ResultService {

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final AssessmentAccess access;

    @Override
    @Transactional(readOnly = true)
    public MyResultsResponse myResults() {
        AppPrincipal student = access.requireStudent();

        Map<Long, List<QuizAttempt>> attemptsByQuiz = attemptRepository
                .findByStudentIdOrderByStartedAtDesc(student.profileId()).stream()
                .filter(a -> a.getStatus().isFinished())
                .collect(Collectors.groupingBy(QuizAttempt::getQuizId));

        List<MyResultsResponse.TestResult> tests = quizRepository.findAllById(attemptsByQuiz.keySet()).stream()
                .sorted(Comparator.comparing(Quiz::getTitle))
                .map(quiz -> {
                    List<QuizAttempt> attempts = attemptsByQuiz.get(quiz.getId());
                    Integer best = resultsVisible(quiz)
                            ? attempts.stream().map(QuizAttempt::getPercentage)
                                    .filter(Objects::nonNull).max(Integer::compare).orElse(null)
                            : null;
                    return new MyResultsResponse.TestResult(
                            quiz.getId(), quiz.getTitle(), quiz.getCourseId(), attempts.size(),
                            best, best == null ? null : quiz.passed(best),
                            quiz.getPassPercentage(), quiz.isMandatory());
                })
                .toList();

        List<Submission> submissions = submissionRepository.findByStudentIdOrderBySubmittedAtDesc(student.profileId());
        Map<Long, Assignment> assignments = assignmentRepository
                .findAllById(submissions.stream().map(Submission::getAssignmentId).toList()).stream()
                .collect(Collectors.toMap(Assignment::getId, Function.identity()));

        List<MyResultsResponse.AssignmentResult> work = submissions.stream()
                .filter(s -> assignments.containsKey(s.getAssignmentId()))
                .map(s -> {
                    Assignment a = assignments.get(s.getAssignmentId());
                    return new MyResultsResponse.AssignmentResult(
                            a.getId(), s.getId(), a.getTitle(), s.getStatus().name(), s.getMarks(),
                            a.getMaxMarks(), s.getFeedback(), s.getSubmittedAt(), s.getEvaluatedAt());
                })
                .toList();

        return new MyResultsResponse(tests, work);
    }

    @Override
    @Transactional(readOnly = true)
    public CompletionResponse completion(Long studentId, Long courseId, Long batchId) {
        SecurityUtils.requireStudentOwnershipOrStaff(studentId);
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        List<String> outstanding = new ArrayList<>();

        // Tests: the best attempt must reach the pass mark.
        List<Quiz> tests = quizRepository.findMandatoryForBatch(courseId, batchId);
        Map<Long, Integer> best = new HashMap<>();
        if (!tests.isEmpty()) {
            for (Object[] row : attemptRepository.bestPercentages(studentId, tests.stream().map(Quiz::getId).toList())) {
                best.put((Long) row[0], row[1] == null ? null : ((Number) row[1]).intValue());
            }
        }
        int testsPassed = 0;
        for (Quiz quiz : tests) {
            // A student asking about themselves must not learn whether they
            // passed a test whose results the trainer is still holding back.
            if (caller.isStudent() && !resultsVisible(quiz)) {
                outstanding.add(quiz.getTitle() + " (result pending)");
                continue;
            }
            Integer percentage = best.get(quiz.getId());
            if (percentage != null && quiz.passed(percentage)) {
                testsPassed++;
            } else {
                outstanding.add(quiz.getTitle());
            }
        }

        // Assignments: handed in and marked. Doc S7.3 asks for them "complete";
        // a submission still waiting for its mark is not complete yet.
        List<Assignment> work = assignmentRepository.findMandatoryForBatch(batchId);
        Map<Long, Submission> submitted = work.isEmpty() ? Map.of()
                : submissionRepository.findByStudentIdAndAssignmentIdIn(studentId,
                                work.stream().map(Assignment::getId).toList())
                        .stream().collect(Collectors.toMap(Submission::getAssignmentId, Function.identity()));
        int evaluated = 0;
        for (Assignment assignment : work) {
            Submission submission = submitted.get(assignment.getId());
            if (submission != null && submission.getStatus() == SubmissionStatus.EVALUATED) {
                evaluated++;
            } else {
                outstanding.add(assignment.getTitle());
            }
        }

        return new CompletionResponse(studentId, courseId, batchId,
                tests.size(), testsPassed, work.size(), evaluated,
                outstanding.isEmpty(), outstanding);
    }

    private static boolean resultsVisible(Quiz quiz) {
        return quiz.isShowResultImmediately() || quiz.getStatus() == QuizStatus.CLOSED;
    }
}
