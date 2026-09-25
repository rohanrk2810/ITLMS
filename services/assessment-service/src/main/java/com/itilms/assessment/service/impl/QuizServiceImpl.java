package com.itilms.assessment.service.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.assessment.client.BatchClient;
import com.itilms.assessment.dto.request.CreateQuizRequest;
import com.itilms.assessment.dto.request.QuestionRequest;
import com.itilms.assessment.dto.response.QuizResponse;
import com.itilms.assessment.dto.response.StudentQuizResponse;
import com.itilms.assessment.entity.AttemptStatus;
import com.itilms.assessment.entity.QuestionType;
import com.itilms.assessment.entity.QuizTestCase;
import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizAttempt;
import com.itilms.assessment.entity.QuizOption;
import com.itilms.assessment.entity.QuizQuestion;
import com.itilms.assessment.entity.QuizStatus;
import com.itilms.assessment.repository.QuizAttemptRepository;
import com.itilms.assessment.repository.QuizQuestionRepository;
import com.itilms.assessment.repository.QuizRepository;
import com.itilms.assessment.service.AssessmentAccess;
import com.itilms.assessment.service.AttemptScorer;
import com.itilms.assessment.service.QuizService;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.code.CodeLanguage;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private static final String SERVICE_NAME = "assessment-service";

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final AssessmentAccess access;
    private final AttemptScorer scorer;
    private final EventPublisher events;

    // -----------------------------------------------------------------
    // Authoring
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public QuizResponse create(CreateQuizRequest request) {
        AppPrincipal caller = access.requireManagesCourseOrBatch(request.courseId(), request.batchId());
        requireValidWindow(request);

        Quiz quiz = quizRepository.save(Quiz.builder()
                .courseId(request.courseId())
                .batchId(request.batchId())
                .title(request.title().trim())
                .instructions(trim(request.instructions()))
                .durationMinutes(request.durationMinutes())
                .passPercentage(request.passPercentage() == null ? 40 : request.passPercentage())
                .attemptsAllowed(request.attemptsAllowed() == null ? 1 : request.attemptsAllowed())
                .availableFrom(request.availableFrom())
                .availableUntil(request.availableUntil())
                .shuffleQuestions(request.shuffleQuestions() == null || request.shuffleQuestions())
                .showResultImmediately(request.showResultImmediately() == null || request.showResultImmediately())
                .mandatory(request.mandatory() == null || request.mandatory())
                .secureMode(Boolean.TRUE.equals(request.secureMode()))
                .maxViolations(request.maxViolations() == null ? 2 : request.maxViolations())
                .requireCamera(Boolean.TRUE.equals(request.requireCamera()))
                .trainerId(caller.trainerIdOrNull())
                .status(QuizStatus.DRAFT)
                .build());

        return QuizResponse.detail(quiz, List.of());
    }

    /**
     * Only a draft can be changed.
     *
     * <p>Once students have sat a test, changing its duration, pass mark or
     * questions would put two students with identical answers on different
     * sides of the pass line. A published test with a mistake is closed and
     * replaced, so the results that exist stay comparable.
     */
    @Override
    @Transactional
    public QuizResponse update(Long id, CreateQuizRequest request) {
        Quiz quiz = requireDraft(id);
        access.requireManagesQuiz(quiz);
        requireValidWindow(request);
        if (!quiz.getCourseId().equals(request.courseId())) {
            throw new BusinessRuleException("A test cannot be moved to another course.");
        }

        quiz.setBatchId(request.batchId());
        quiz.setTitle(request.title().trim());
        quiz.setInstructions(trim(request.instructions()));
        quiz.setDurationMinutes(request.durationMinutes());
        if (request.passPercentage() != null) {
            quiz.setPassPercentage(request.passPercentage());
        }
        if (request.attemptsAllowed() != null) {
            quiz.setAttemptsAllowed(request.attemptsAllowed());
        }
        quiz.setAvailableFrom(request.availableFrom());
        quiz.setAvailableUntil(request.availableUntil());
        if (request.shuffleQuestions() != null) {
            quiz.setShuffleQuestions(request.shuffleQuestions());
        }
        if (request.showResultImmediately() != null) {
            quiz.setShowResultImmediately(request.showResultImmediately());
        }
        if (request.mandatory() != null) {
            quiz.setMandatory(request.mandatory());
        }
        if (request.secureMode() != null) {
            quiz.setSecureMode(request.secureMode());
        }
        if (request.maxViolations() != null) {
            quiz.setMaxViolations(request.maxViolations());
        }
        if (request.requireCamera() != null) {
            quiz.setRequireCamera(request.requireCamera());
        }
        quizRepository.save(quiz);
        return detail(quiz);
    }

    @Override
    @Transactional
    public QuizResponse addQuestion(Long quizId, QuestionRequest request) {
        Quiz quiz = requireDraft(quizId);
        access.requireManagesQuiz(quiz);

        QuizQuestion question = QuizQuestion.builder()
                .quizId(quizId)
                .sequenceNo(questionRepository.maxSequenceNo(quizId) + 1)
                .build();
        apply(question, request);
        questionRepository.save(question);
        quizRepository.refreshTotalMarks(quizId);

        return detail(requireQuiz(quizId));
    }

    @Override
    @Transactional
    public QuizResponse updateQuestion(Long questionId, QuestionRequest request) {
        QuizQuestion question = requireQuestion(questionId);
        Quiz quiz = requireDraft(question.getQuizId());
        access.requireManagesQuiz(quiz);

        question.getOptions().clear();
        question.getTestCases().clear();
        question.getAcceptedAnswers().clear();
        // Flush the removals before re-adding, so the unique (question, sequence)
        // index never sees the old and new option 1 (or test case 1) at the same moment.
        questionRepository.saveAndFlush(question);
        apply(question, request);
        questionRepository.save(question);
        quizRepository.refreshTotalMarks(quiz.getId());

        return detail(requireQuiz(quiz.getId()));
    }

    @Override
    @Transactional
    public QuizResponse deleteQuestion(Long questionId) {
        QuizQuestion question = requireQuestion(questionId);
        Quiz quiz = requireDraft(question.getQuizId());
        access.requireManagesQuiz(quiz);

        questionRepository.delete(question);
        questionRepository.flush();
        quizRepository.refreshTotalMarks(quiz.getId());
        return detail(requireQuiz(quiz.getId()));
    }

    @Override
    @Transactional
    public QuizResponse publish(Long id) {
        Quiz quiz = requireDraft(id);
        access.requireManagesQuiz(quiz);

        List<QuizQuestion> questions = questionRepository.findWithOptions(id);
        if (questions.isEmpty()) {
            throw new BusinessRuleException("Add at least one question before publishing the test.");
        }
        if (quiz.getAvailableUntil() != null && quiz.getAvailableUntil().isBefore(Instant.now())) {
            throw new BusinessRuleException("The test's closing time has already passed.");
        }

        quiz.publish(Instant.now());
        quizRepository.save(quiz);

        if (quiz.getBatchId() != null) {
            events.notifyBatch(quiz.getBatchId(), "TEST",
                    "New test: " + quiz.getTitle(),
                    "%d questions, %d minutes.".formatted(questions.size(), quiz.getDurationMinutes()),
                    "/student/tests/" + quiz.getId());
        }
        events.audit(SERVICE_NAME, "QUIZ_PUBLISHED", "Quiz", quiz.getId(), null,
                Map.of("questions", questions.size(), "totalMarks", quiz.getTotalMarks()));
        return QuizResponse.detail(quiz, questions);
    }

    @Override
    @Transactional
    public QuizResponse close(Long id) {
        Quiz quiz = requireQuiz(id);
        access.requireManagesQuiz(quiz);
        if (quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw new BusinessRuleException("Only a published test can be closed.");
        }

        quiz.setStatus(QuizStatus.CLOSED);
        quizRepository.save(quiz);

        // Anyone mid-test is scored on what they have saved. Leaving them open
        // would mean a closed test that still accepts answers.
        int closed = scorer.finishOpenAttempts(quiz);

        if (quiz.getBatchId() != null && !quiz.isShowResultImmediately()) {
            events.notifyBatch(quiz.getBatchId(), "TEST_RESULT",
                    "Results released: " + quiz.getTitle(),
                    "Your result for this test is now available.",
                    "/student/results");
        }
        log.info("Closed test {}; {} running attempt(s) scored as they stood", id, closed);
        return detail(quiz);
    }

    // -----------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public QuizResponse get(Long id) {
        Quiz quiz = requireQuiz(id);
        access.requireManagesQuiz(quiz);
        return detail(quiz);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuizResponse> list(Long courseId, Pageable pageable) {
        Page<Quiz> page;
        if (courseId == null) {
            // Every test in the institute is a staff view; a trainer lists by course.
            if (!SecurityUtils.requirePrincipal().isStaff()) {
                throw new BusinessRuleException("Choose a course to list its tests.");
            }
            page = quizRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            access.requireManagesCourseOrBatch(courseId, null);
            page = quizRepository.findByCourseIdOrderByCreatedAtDesc(courseId, pageable);
        }

        Map<Long, Integer> questionCounts = new HashMap<>();
        List<Long> ids = page.map(Quiz::getId).getContent();
        if (!ids.isEmpty()) {
            for (Object[] row : questionRepository.countsByQuiz(ids)) {
                questionCounts.put((Long) row[0], ((Number) row[1]).intValue());
            }
        }
        return PageResponse.from(page, q -> QuizResponse.summary(q, questionCounts.getOrDefault(q.getId(), 0)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentQuizResponse> availableToMe() {
        AppPrincipal student = access.requireStudent();
        List<BatchClient.BatchSummary> batches = access.myBatches();
        if (batches.isEmpty()) {
            return List.of();
        }
        Set<Long> courseIds = batches.stream().map(BatchClient.BatchSummary::courseId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> batchIds = batches.stream().map(BatchClient.BatchSummary::id).collect(Collectors.toSet());
        if (courseIds.isEmpty()) {
            return List.of();
        }

        List<Quiz> quizzes = quizRepository.findAvailableFor(courseIds, batchIds);
        if (quizzes.isEmpty()) {
            return List.of();
        }
        List<Long> quizIds = quizzes.stream().map(Quiz::getId).toList();

        Map<Long, Integer> best = new HashMap<>();
        for (Object[] row : attemptRepository.bestPercentages(student.profileId(), quizIds)) {
            best.put((Long) row[0], row[1] == null ? null : ((Number) row[1]).intValue());
        }
        // One query for every attempt on the page, grouped here, rather than
        // one query per test.
        Map<Long, List<QuizAttempt>> attemptsByQuiz = attemptRepository
                .findByStudentIdAndQuizIdIn(student.profileId(), quizIds).stream()
                .collect(Collectors.groupingBy(QuizAttempt::getQuizId));

        Instant now = Instant.now();
        return quizzes.stream().map(q -> {
            List<QuizAttempt> mine = attemptsByQuiz.getOrDefault(q.getId(), List.of());
            Long running = mine.stream()
                    .filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS && !a.hasExpired(now))
                    .map(QuizAttempt::getId).findFirst().orElse(null);
            boolean visible = q.isShowResultImmediately() || q.getStatus() == QuizStatus.CLOSED;
            return StudentQuizResponse.of(q, mine.size(), running, best.get(q.getId()), visible, now);
        }).toList();
    }

    // -----------------------------------------------------------------

    /**
     * Fills a question from the request, refusing shapes that cannot be marked.
     *
     * <p>A single-choice question with two "correct" options, or none, has no
     * right answer that a student could give. Catching it here, while the
     * trainer is writing, beats discovering it when forty students all score
     * zero on question 7.
     */
    private void apply(QuizQuestion question, QuestionRequest request) {
        QuestionType type = parseType(request.type());
        List<QuestionRequest.OptionRequest> options = request.options() == null ? List.of() : request.options();
        List<String> accepted = request.acceptedAnswers() == null ? List.of() : request.acceptedAnswers().stream()
                .filter(a -> a != null && !a.isBlank()).map(String::trim).distinct().toList();
        List<QuestionRequest.TestCaseRequest> cases = request.testCases() == null ? List.of() : request.testCases();

        if (type.isChoice()) {
            requireOnlyChoiceFields(type, accepted, cases, request);
            long correctCount = options.stream().filter(o -> Boolean.TRUE.equals(o.correct())).count();
            if (options.size() < 2) {
                throw new BusinessRuleException("A %s question needs between 2 and 8 options.".formatted(type));
            }
            if (correctCount == 0) {
                throw new BusinessRuleException("Mark at least one option as correct.");
            }
            if (type != QuestionType.MULTI_CHOICE && correctCount > 1) {
                throw new BusinessRuleException(
                        "A %s question has exactly one correct option; use MULTI_CHOICE for more.".formatted(type));
            }
            if (type == QuestionType.TRUE_FALSE && options.size() != 2) {
                throw new BusinessRuleException("A true/false question has exactly two options.");
            }
        } else if (type == QuestionType.SHORT_ANSWER) {
            if (!options.isEmpty() || !cases.isEmpty() || request.codeLanguage() != null || request.starterCode() != null) {
                throw new BusinessRuleException("A short-answer question takes accepted answers only.");
            }
            if (accepted.isEmpty()) {
                throw new BusinessRuleException("Give at least one accepted answer, so the question can be marked.");
            }
        } else {
            if (!options.isEmpty() || !accepted.isEmpty()) {
                throw new BusinessRuleException("A coding question takes a language, starter code and test cases only.");
            }
            if (cases.isEmpty()) {
                throw new BusinessRuleException("Add at least one test case, so the code can be marked.");
            }
        }

        question.setQuestionText(request.questionText().trim());
        question.setType(type);
        question.setMarks(request.marks() == null ? 1 : request.marks());
        question.setExplanation(trim(request.explanation()));

        int sequence = 1;
        for (QuestionRequest.OptionRequest option : options) {
            question.addOption(QuizOption.builder()
                    .optionText(option.optionText().trim())
                    .correct(Boolean.TRUE.equals(option.correct()))
                    .sequenceNo(sequence++)
                    .build());
        }

        question.getAcceptedAnswers().clear();
        question.getAcceptedAnswers().addAll(accepted);

        question.setCodeLanguage(type == QuestionType.CODING ? parseLanguage(request.codeLanguage()) : null);
        question.setStarterCode(type == QuestionType.CODING ? trim(request.starterCode()) : null);
        sequence = 1;
        for (QuestionRequest.TestCaseRequest testCase : cases) {
            question.getTestCases().add(QuizTestCase.builder()
                    .sequenceNo(sequence++)
                    .input(testCase.input() == null ? "" : testCase.input())
                    .expectedOutput(testCase.expectedOutput())
                    .hidden(Boolean.TRUE.equals(testCase.hidden()))
                    .weight(testCase.weight() == null ? 1 : testCase.weight())
                    .build());
        }
    }

    private static void requireOnlyChoiceFields(QuestionType type, List<String> accepted,
                                                List<QuestionRequest.TestCaseRequest> cases, QuestionRequest request) {
        if (!accepted.isEmpty() || !cases.isEmpty() || request.codeLanguage() != null || request.starterCode() != null) {
            throw new BusinessRuleException("A %s question takes options only.".formatted(type));
        }
    }

    private static CodeLanguage parseLanguage(String value) {
        return CodeLanguage.parse(value).orElseThrow(() ->
                new BusinessRuleException("Choose the coding language: " + CodeLanguage.allowedList() + "."));
    }

    private QuestionType parseType(String value) {
        if (value == null || value.isBlank()) {
            return QuestionType.SINGLE_CHOICE;
        }
        try {
            return QuestionType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(
                    "Question type must be SINGLE_CHOICE, MULTI_CHOICE, TRUE_FALSE, SHORT_ANSWER or CODING.");
        }
    }

    private void requireValidWindow(CreateQuizRequest request) {
        if (request.availableFrom() != null && request.availableUntil() != null
                && !request.availableUntil().isAfter(request.availableFrom())) {
            throw new BusinessRuleException("The test must close after it opens.");
        }
    }

    private QuizResponse detail(Quiz quiz) {
        return QuizResponse.detail(quiz, questionRepository.findWithOptions(quiz.getId()));
    }

    private Quiz requireQuiz(Long id) {
        return quizRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Test", id));
    }

    private Quiz requireDraft(Long id) {
        Quiz quiz = requireQuiz(id);
        if (quiz.getStatus() != QuizStatus.DRAFT) {
            throw new BusinessRuleException(
                    "This test has been published and can no longer be changed. Close it and create a new one.");
        }
        return quiz;
    }

    private QuizQuestion requireQuestion(Long id) {
        return questionRepository.findWithOptionsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question", id));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
