package com.itilms.assessment.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.assessment.dto.request.SubmitAttemptRequest;
import com.itilms.assessment.dto.request.ViolationRequest;
import com.itilms.assessment.dto.response.AnswerSaveResponse;
import com.itilms.assessment.dto.response.AttemptResultResponse;
import com.itilms.assessment.dto.response.AttemptViewResponse;
import com.itilms.assessment.dto.response.CodingRunResponse;
import com.itilms.assessment.dto.response.ViolationResponse;
import com.itilms.assessment.entity.AttemptStatus;
import com.itilms.assessment.entity.QuestionType;
import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizAnswer;
import com.itilms.assessment.entity.QuizAttempt;
import com.itilms.assessment.entity.QuizQuestion;
import com.itilms.assessment.entity.QuizStatus;
import com.itilms.assessment.entity.QuizViolation;
import com.itilms.assessment.repository.QuizAnswerRepository;
import com.itilms.assessment.repository.QuizAttemptRepository;
import com.itilms.assessment.repository.QuizQuestionRepository;
import com.itilms.assessment.repository.QuizRepository;
import com.itilms.assessment.repository.QuizViolationRepository;
import com.itilms.assessment.service.AssessmentAccess;
import com.itilms.assessment.service.AttemptScorer;
import com.itilms.assessment.service.AttemptService;
import com.itilms.assessment.service.CodingJudge;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptServiceImpl implements AttemptService {

    /**
     * How late a final submission may arrive and still count.
     *
     * <p>A student who presses Submit at 10:59:58 should not lose their answers
     * to network latency. A minute covers a slow connection; it does not cover
     * someone who kept going after the timer hit zero.
     */
    static final Duration SUBMIT_GRACE = Duration.ofSeconds(60);

    private static final int MAX_SHORT_ANSWER = 1000;

    /** Leaving a window is often reported twice (the tab hides, then the window blurs): one leaving counts once. */
    static final Duration VIOLATION_MERGE_WINDOW = Duration.ofSeconds(3);
    /** A browser stuck in a loop must not fill the table. */
    private static final int MAX_STORED_VIOLATIONS = 300;

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAnswerRepository answerRepository;
    private final AssessmentAccess access;
    private final AttemptScorer scorer;
    private final CodingJudge judge;
    private final QuizViolationRepository violations;

    // -----------------------------------------------------------------
    // Sitting the test
    // -----------------------------------------------------------------

    // noRollbackFor: when time has run out these methods score the attempt and
    // then refuse the request. The refusal must not roll the scoring back with it.
    // Every other refusal they make happens before anything is written.
    @Override
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public AttemptViewResponse start(Long quizId) {
        AppPrincipal student = access.requireStudent();
        Quiz quiz = requireQuiz(quizId);
        Instant now = Instant.now();

        if (!quiz.isOpenAt(now)) {
            throw new BusinessRuleException("TEST_NOT_OPEN", quiz.getStatus() == QuizStatus.PUBLISHED
                    ? "This test is not open right now."
                    : "This test is not available.");
        }
        Long batchId = access.studentBatchFor(quiz).orElseThrow(() ->
                new ForbiddenOperationException("This test is not set for any batch you are in."));

        var running = attemptRepository.findFirstByQuizIdAndStudentIdAndStatusOrderByAttemptNoDesc(
                quizId, student.profileId(), AttemptStatus.IN_PROGRESS);
        if (running.isPresent()) {
            QuizAttempt attempt = running.get();
            if (!attempt.hasExpired(now)) {
                return paperFor(attempt, quiz, now);
            }
            // Time ran out while they were away. Score it, then fall through:
            // they may still have an attempt left.
            scorer.finish(attempt, quiz, true);
        }

        long used = attemptRepository.countByQuizIdAndStudentId(quizId, student.profileId());
        if (used >= quiz.getAttemptsAllowed()) {
            throw new BusinessRuleException("NO_ATTEMPTS_LEFT",
                    "You have used all %d attempt(s) for this test.".formatted(quiz.getAttemptsAllowed()));
        }

        Instant deadline = QuizAttempt.deadline(now, quiz.getDurationMinutes());
        // A test that closes at 11:00 closes at 11:00 for someone who starts at
        // 10:50 with a 30-minute paper too.
        if (quiz.getAvailableUntil() != null && quiz.getAvailableUntil().isBefore(deadline)) {
            deadline = quiz.getAvailableUntil();
        }

        QuizAttempt attempt = attemptRepository.save(QuizAttempt.builder()
                .quizId(quizId)
                .studentId(student.profileId())
                .studentUserId(student.userId())
                .studentName(student.fullName())
                .batchId(batchId)
                .attemptNo(attemptRepository.lastAttemptNo(quizId, student.profileId()) + 1)
                .startedAt(now)
                .expiresAt(deadline)
                .status(AttemptStatus.IN_PROGRESS)
                .build());

        log.info("Student {} started attempt {} of test {}", student.profileId(), attempt.getAttemptNo(), quizId);
        return paperFor(attempt, quiz, now);
    }

    @Override
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public AttemptViewResponse paper(Long attemptId) {
        QuizAttempt attempt = requireOwnAttempt(attemptId);
        Quiz quiz = requireQuiz(attempt.getQuizId());
        Instant now = Instant.now();

        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS && attempt.hasExpired(now)) {
            scorer.finish(attempt, quiz, true);
        }
        if (attempt.getStatus().isFinished()) {
            throw new BusinessRuleException("ATTEMPT_FINISHED", "This attempt has finished. Open its result instead.");
        }
        return paperFor(attempt, quiz, now);
    }

    @Override
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public AnswerSaveResponse saveAnswers(Long attemptId, SubmitAttemptRequest request) {
        QuizAttempt attempt = requireOwnAttempt(attemptId);
        Quiz quiz = requireQuiz(attempt.getQuizId());
        Instant now = Instant.now();

        requireRunning(attempt, quiz, now, Duration.ZERO);
        List<QuizQuestion> questions = questionRepository.findWithOptions(quiz.getId());
        int answered = store(attempt, questions, request, now);

        return new AnswerSaveResponse(attempt.getId(), answered, questions.size(),
                attempt.getExpiresAt(), attempt.secondsRemaining(now));
    }

    @Override
    @Transactional
    public ViolationResponse.Outcome recordViolation(Long attemptId, ViolationRequest request) {
        QuizAttempt attempt = requireOwnAttempt(attemptId);
        Quiz quiz = requireQuiz(attempt.getQuizId());
        Instant now = Instant.now();

        // Not a secure test, or the sitting is already over: nothing to enforce, and no reason to make the
        // page handle an error for a report it had every right to send.
        if (!quiz.isSecureMode() || attempt.getStatus().isFinished() || attempt.hasExpired(now)) {
            return outcome(attempt, quiz, false);
        }

        boolean counts = request.type().counts() && !mergesWithLastLeaving(attempt, now);
        if (violations.countByAttemptId(attemptId) < MAX_STORED_VIOLATIONS) {
            violations.save(QuizViolation.builder().attemptId(attemptId).type(request.type()).counted(counts)
                    .detail(trimToNull(request.detail())).occurredAt(now).clientAt(request.clientAt()).build());
        }
        if (!counts) {
            return outcome(attempt, quiz, false);
        }

        attempt.setViolationCount(attempt.getViolationCount() + 1);
        if (attempt.getViolationCount() >= quiz.getMaxViolations()) {
            scorer.terminate(attempt, quiz, "Left the test window %d times (the limit is %d)."
                    .formatted(attempt.getViolationCount(), quiz.getMaxViolations()));
            log.info("Attempt {} terminated after {} violations", attemptId, attempt.getViolationCount());
        } else {
            attemptRepository.save(attempt);
        }
        return outcome(attempt, quiz, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ViolationResponse.Entry> violations(Long attemptId) {
        QuizAttempt attempt = requireAttempt(attemptId);
        access.requireManagesQuiz(requireQuiz(attempt.getQuizId()));
        return violations.findByAttemptIdOrderByOccurredAtAsc(attemptId).stream()
                .map(ViolationResponse.Entry::from).toList();
    }

    private boolean mergesWithLastLeaving(QuizAttempt attempt, Instant now) {
        return violations.findFirstByAttemptIdAndCountedTrueOrderByOccurredAtDesc(attempt.getId())
                .map(last -> Duration.between(last.getOccurredAt(), now).compareTo(VIOLATION_MERGE_WINDOW) < 0)
                .orElse(false);
    }

    private static ViolationResponse.Outcome outcome(QuizAttempt attempt, Quiz quiz, boolean counted) {
        boolean terminated = attempt.getStatus() == AttemptStatus.TERMINATED;
        // Further warnings before the attempt ends: with a limit of 2 the first violation is the warning
        // and the second ends it, so after the first there are none left.
        int warningsLeft = Math.max(0, quiz.getMaxViolations() - 1 - attempt.getViolationCount());
        String message = null;
        if (terminated) {
            message = "Your test has been ended because you left the test window too many times.";
        } else if (counted) {
            message = warningsLeft == 0
                    ? "Warning: Please do not leave the test window. This is your last warning."
                    : "Warning: Please do not leave the test window.";
        }
        return new ViolationResponse.Outcome(counted, attempt.getViolationCount(), quiz.getMaxViolations(),
                warningsLeft, terminated, message);
    }

    private static String trimToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    /**
     * Runs the tests for one coding question, keeps the code as the student's answer and stores the verdict
     * beside the exact code it came from.
     *
     * <p>Refused runs (runner busy, over the limit, down) throw before anything is written, so the code the
     * student already had saved and its earlier verdict are untouched.
     */
    @Override
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public CodingRunResponse runTests(Long attemptId, Long questionId, String sourceCode) {
        QuizAttempt attempt = requireOwnAttempt(attemptId);
        Quiz quiz = requireQuiz(attempt.getQuizId());
        Instant now = Instant.now();
        requireRunning(attempt, quiz, now, Duration.ZERO);

        QuizQuestion question = questionRepository.findWithOptions(quiz.getId()).stream()
                .filter(q -> q.getId().equals(questionId)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("INVALID_ANSWER",
                        "Question %d is not part of this test.".formatted(questionId)));
        if (question.getType() != QuestionType.CODING) {
            throw new BusinessRuleException("INVALID_ANSWER", "Question %d is not a coding question.".formatted(questionId));
        }

        CodingJudge.Verdict verdict = judge.judge(question, sourceCode);
        recordVerdict(attempt, question, sourceCode, verdict, now);
        return CodingRunResponse.of(questionId, verdict);
    }

    private void recordVerdict(QuizAttempt attempt, QuizQuestion question, String sourceCode,
                               CodingJudge.Verdict verdict, Instant now) {
        QuizAnswer row = answerRepository.findByAttemptId(attempt.getId()).stream()
                .filter(a -> a.getQuestionId().equals(question.getId())).findFirst()
                .orElseGet(() -> QuizAnswer.builder().attemptId(attempt.getId()).questionId(question.getId()).build());
        row.setAnswerText(sourceCode);
        row.setTestsPassed(verdict.passedCount());
        row.setTestsTotal(verdict.cases().size());
        row.setTestedSourceHash(CodingJudge.sha256(sourceCode));
        row.setTestedMarks(question.codingMarks(verdict.passedWeight()));
        row.setAnsweredAt(now);
        answerRepository.save(row);
    }

    /**
     * Before a submission is scored, runs the tests for any coding answer whose code has changed since its
     * last run (or never ran). A run that is refused leaves the earlier verdict in place: the student is not
     * marked down for the runner being busy, they are marked on the last version that was tested.
     */
    private void refreshCodingVerdicts(QuizAttempt attempt, List<QuizQuestion> questions, Instant now) {
        Map<Long, QuizQuestion> coding = questions.stream().filter(q -> q.getType() == QuestionType.CODING)
                .collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));
        if (coding.isEmpty()) {
            return;
        }
        for (QuizAnswer answer : answerRepository.findByAttemptId(attempt.getId())) {
            QuizQuestion question = coding.get(answer.getQuestionId());
            String code = answer.getAnswerText();
            if (question == null || code == null || code.isBlank()
                    || CodingJudge.sha256(code).equals(answer.getTestedSourceHash())) {
                continue;
            }
            try {
                recordVerdict(attempt, question, code, judge.judge(question, code), now);
            } catch (BusinessRuleException | FeignException e) {
                log.warn("Tests for question {} of attempt {} could not be run at submission: {}",
                        question.getId(), attempt.getId(), e.getMessage());
            }
        }
    }

    /**
     * Stores the final answers and scores the attempt.
     *
     * <p>A submission arriving within {@link #SUBMIT_GRACE} of the deadline is
     * accepted. One arriving later is not: the attempt is scored on what had
     * been saved by the deadline, and the late answers are discarded.
     */
    @Override
    @Transactional
    public AttemptResultResponse submit(Long attemptId, SubmitAttemptRequest request) {
        QuizAttempt attempt = requireOwnAttempt(attemptId);
        Quiz quiz = requireQuiz(attempt.getQuizId());
        Instant now = Instant.now();

        if (attempt.getStatus().isFinished()) {
            // A double click on Submit. The first one counted; show its result.
            return resultFor(attempt, quiz, false);
        }

        List<QuizQuestion> questions = questionRepository.findWithOptions(quiz.getId());
        boolean inTime = !now.isAfter(attempt.getExpiresAt().plus(SUBMIT_GRACE));
        if (inTime) {
            store(attempt, questions, request, now);
            refreshCodingVerdicts(attempt, questions, now);
            scorer.finish(attempt, quiz, false);
        } else {
            log.info("Attempt {} submitted {}s after its deadline; scored on answers saved before it",
                    attemptId, Duration.between(attempt.getExpiresAt(), now).toSeconds());
            scorer.finish(attempt, quiz, true);
        }
        return resultFor(attempt, quiz, false);
    }

    // -----------------------------------------------------------------
    // Results
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public AttemptResultResponse result(Long attemptId) {
        QuizAttempt attempt = requireAttempt(attemptId);
        Quiz quiz = requireQuiz(attempt.getQuizId());
        AppPrincipal caller = SecurityUtils.requirePrincipal();

        boolean staffView;
        if (caller.isStudent()) {
            requireOwner(attempt, caller);
            staffView = false;
        } else {
            access.requireManagesQuiz(quiz);
            staffView = true;
        }
        if (!attempt.getStatus().isFinished()) {
            throw new BusinessRuleException("ATTEMPT_RUNNING", "This attempt is still in progress.");
        }
        return resultFor(attempt, quiz, staffView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttemptResultResponse> myAttempts(Long quizId) {
        AppPrincipal student = access.requireStudent();
        Quiz quiz = requireQuiz(quizId);
        boolean visible = resultsVisibleToStudents(quiz);
        return attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNoDesc(quizId, student.profileId())
                .stream()
                .filter(a -> a.getStatus().isFinished())
                .map(a -> AttemptResultResponse.of(a, quiz, visible, null, List.of()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttemptResultResponse> quizResults(Long quizId) {
        Quiz quiz = requireQuiz(quizId);
        access.requireManagesQuiz(quiz);
        return attemptRepository.findByQuizIdAndStatusInOrderByScoreDesc(
                        quizId, List.of(AttemptStatus.SUBMITTED, AttemptStatus.EXPIRED, AttemptStatus.TERMINATED))
                .stream()
                .map(a -> AttemptResultResponse.of(a, quiz, true, null, List.of()))
                .toList();
    }

    // -----------------------------------------------------------------

    /**
     * Upserts the answers in the request.
     *
     * <p>Every question and option id is checked against this test's own paper.
     * An answer naming a question from another test, or an option from another
     * question, is refused outright rather than scored as wrong: it is not a
     * mistake a student makes by clicking, so it is treated as the tampering it
     * is.
     *
     * @return how many questions now have an answer
     */
    private int store(QuizAttempt attempt, List<QuizQuestion> questions, SubmitAttemptRequest request,
                      Instant now) {
        Map<Long, QuizQuestion> paper = questions.stream()
                .collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));
        Map<Long, QuizAnswer> saved = new HashMap<>();
        answerRepository.findByAttemptId(attempt.getId()).forEach(a -> saved.put(a.getQuestionId(), a));

        List<QuizAnswer> toSave = new ArrayList<>();
        List<QuizAnswer> toDelete = new ArrayList<>();

        for (SubmitAttemptRequest.Answer answer : request.answers() == null
                ? List.<SubmitAttemptRequest.Answer>of() : request.answers()) {
            QuizQuestion question = paper.get(answer.questionId());
            if (question == null) {
                throw new BusinessRuleException("INVALID_ANSWER", "Question %d is not part of this test."
                        .formatted(answer.questionId()));
            }
            Set<Long> selected = answer.selectedOptionIds() == null ? Set.of() : answer.selectedOptionIds();

            if (question.getType().isText()) {
                if (!selected.isEmpty()) {
                    throw new BusinessRuleException("INVALID_ANSWER",
                            "Question %d is answered in text, not by choosing options.".formatted(question.getId()));
                }
                String text = answer.answerText();
                if (question.getType() == QuestionType.SHORT_ANSWER && text != null && text.length() > MAX_SHORT_ANSWER) {
                    throw new BusinessRuleException("INVALID_ANSWER",
                            "A short answer is limited to %d characters.".formatted(MAX_SHORT_ANSWER));
                }
                QuizAnswer existing = saved.get(question.getId());
                if (text == null || text.isBlank()) {
                    if (existing != null) {
                        toDelete.add(existing);
                        saved.remove(question.getId());
                    }
                    continue;
                }
                QuizAnswer row = existing != null ? existing
                        : QuizAnswer.builder().attemptId(attempt.getId()).questionId(question.getId()).build();
                row.setAnswerText(text);
                row.setAnsweredAt(now);
                toSave.add(row);
                saved.put(question.getId(), row);
                continue;
            }
            if (answer.answerText() != null && !answer.answerText().isBlank()) {
                throw new BusinessRuleException("INVALID_ANSWER",
                        "Question %d is answered by choosing options, not with text.".formatted(question.getId()));
            }
            if (!question.owns(selected)) {
                throw new BusinessRuleException("INVALID_ANSWER",
                        "An option chosen for question %d does not belong to it.".formatted(question.getId()));
            }
            if (!question.getType().allowsMultipleSelections() && selected.size() > 1) {
                throw new BusinessRuleException("INVALID_ANSWER",
                        "Question %d accepts only one answer.".formatted(question.getId()));
            }

            QuizAnswer existing = saved.get(question.getId());
            if (selected.isEmpty()) {
                if (existing != null) {
                    toDelete.add(existing);
                    saved.remove(question.getId());
                }
                continue;
            }
            QuizAnswer row = existing != null ? existing
                    : QuizAnswer.builder().attemptId(attempt.getId()).questionId(question.getId()).build();
            row.getSelectedOptionIds().clear();
            row.getSelectedOptionIds().addAll(selected);
            row.setAnsweredAt(now);
            toSave.add(row);
            saved.put(question.getId(), row);
        }

        answerRepository.deleteAll(toDelete);
        answerRepository.saveAll(toSave);
        return saved.size();
    }

    private void requireRunning(QuizAttempt attempt, Quiz quiz, Instant now, Duration grace) {
        if (attempt.getStatus().isFinished()) {
            throw new BusinessRuleException("ATTEMPT_FINISHED", "This attempt has already finished.");
        }
        if (now.isAfter(attempt.getExpiresAt().plus(grace))) {
            scorer.finish(attempt, quiz, true);
            throw new BusinessRuleException("TIME_UP",
                    "Time is up. Your attempt was scored on the answers saved before the deadline.");
        }
    }

    /**
     * The paper in a stable order.
     *
     * <p>When shuffling is on, the order is seeded by the attempt id: each
     * student sees a different order from their neighbour, but the same order
     * every time they reload - a paper that reshuffles on refresh is how
     * students lose their place.
     */
    private AttemptViewResponse paperFor(QuizAttempt attempt, Quiz quiz, Instant now) {
        List<QuizQuestion> questions = new ArrayList<>(questionRepository.findWithOptions(quiz.getId()));
        if (quiz.isShuffleQuestions()) {
            Collections.shuffle(questions, new Random(attempt.getId()));
        }
        return AttemptViewResponse.of(attempt, quiz, questions, answerRepository.findByAttemptId(attempt.getId()), now);
    }

    private AttemptResultResponse resultFor(QuizAttempt attempt, Quiz quiz, boolean staffView) {
        boolean visible = staffView || resultsVisibleToStudents(quiz);
        List<QuizQuestion> questions = visible ? questionRepository.findWithOptions(quiz.getId()) : null;
        List<QuizAnswer> answers = visible ? answerRepository.findByAttemptId(attempt.getId()) : List.of();
        return AttemptResultResponse.of(attempt, quiz, visible, questions, answers);
    }

    /** Held back until the test closes, when the trainer asked for that. */
    private boolean resultsVisibleToStudents(Quiz quiz) {
        return quiz.isShowResultImmediately() || quiz.getStatus() == QuizStatus.CLOSED;
    }

    private QuizAttempt requireOwnAttempt(Long attemptId) {
        AppPrincipal student = access.requireStudent();
        QuizAttempt attempt = requireAttempt(attemptId);
        requireOwner(attempt, student);
        return attempt;
    }

    private void requireOwner(QuizAttempt attempt, AppPrincipal student) {
        if (!attempt.getStudentId().equals(student.profileId())) {
            // 404 rather than 403: whether someone else's attempt id exists is
            // itself nothing a student needs to learn.
            throw new ResourceNotFoundException("Attempt", attempt.getId());
        }
    }

    private QuizAttempt requireAttempt(Long id) {
        return attemptRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Attempt", id));
    }

    private Quiz requireQuiz(Long id) {
        return quizRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Test", id));
    }
}
