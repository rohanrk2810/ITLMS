package com.itilms.assessment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.assessment.entity.AttemptStatus;
import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizAnswer;
import com.itilms.assessment.entity.QuizAttempt;
import com.itilms.assessment.entity.QuizQuestion;
import com.itilms.assessment.repository.QuizAnswerRepository;
import com.itilms.assessment.repository.QuizAttemptRepository;
import com.itilms.assessment.repository.QuizQuestionRepository;
import com.itilms.assessment.repository.QuizRepository;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.QuizAttemptCompletedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns saved answers into a score. The only place in IT-ILMS that does.
 *
 * <p>Three things end an attempt - the student submitting, the clock running
 * out, and the trainer closing the test - and all three come here, so a
 * student's result cannot depend on which of them happened. The score is
 * computed from the stored answer key and the answers saved on the server; the
 * browser never supplies a mark, a total, or a verdict (Doc S14).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttemptScorer {

    private final QuizAttemptRepository attemptRepository;
    private final QuizAnswerRepository answerRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizRepository quizRepository;
    private final EventPublisher events;

    /**
     * Scores the attempt and closes it. Does nothing to one already finished.
     *
     * @param expired true when the attempt ended without the student submitting it
     */
    @Transactional
    public QuizAttempt finish(QuizAttempt attempt, Quiz quiz, boolean expired) {
        if (attempt.getStatus().isFinished()) {
            return attempt;
        }

        Map<Long, QuizQuestion> questions = questionRepository.findWithOptions(quiz.getId()).stream()
                .collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));
        List<QuizAnswer> answers = answerRepository.findByAttemptId(attempt.getId());

        int score = 0;
        for (QuizAnswer answer : answers) {
            QuizQuestion question = questions.get(answer.getQuestionId());
            int marks = question == null ? 0 : question.scoreFor(answer.getSelectedOptionIds());
            answer.setMarksAwarded(marks);
            answer.setCorrect(question != null && marks == question.getMarks());
            score += marks;
        }
        answerRepository.saveAll(answers);

        Instant now = Instant.now();
        attempt.complete(score, quiz.getTotalMarks(), quiz.getPassPercentage(), now, expired);
        attempt = attemptRepository.save(attempt);

        events.publishAfterCommit(KafkaTopics.QUIZ_ATTEMPT_COMPLETED, String.valueOf(attempt.getStudentId()),
                new QuizAttemptCompletedEvent(
                        DomainEvent.newId(), now, attempt.getId(), quiz.getId(), quiz.getCourseId(),
                        attempt.getStudentId(), attempt.getStudentUserId(), quiz.getTitle(),
                        BigDecimal.valueOf(attempt.getScore()), BigDecimal.valueOf(attempt.getPercentage()),
                        Boolean.TRUE.equals(attempt.getPassed()), attempt.getAttemptNo()));

        log.info("Attempt {} on test {} {}: {}/{} ({}%)", attempt.getId(), quiz.getId(),
                expired ? "expired" : "submitted", attempt.getScore(), quiz.getTotalMarks(),
                attempt.getPercentage());
        return attempt;
    }

    /** Ends every running attempt on a test - used when the trainer closes it. */
    @Transactional
    public int finishOpenAttempts(Quiz quiz) {
        List<QuizAttempt> open = attemptRepository.findByQuizIdAndStatusInOrderByScoreDesc(
                quiz.getId(), List.of(AttemptStatus.IN_PROGRESS));
        open.forEach(attempt -> finish(attempt, quiz, true));
        return open.size();
    }

    /**
     * Ends attempts whose time ran out without a submission.
     *
     * <p>Each is scored on what the student had saved. Grouped by test so a
     * test with forty abandoned attempts loads its answer key once.
     */
    @Transactional
    public int expireOverdue(Instant now) {
        List<QuizAttempt> overdue = attemptRepository.findExpired(now);
        if (overdue.isEmpty()) {
            return 0;
        }
        Map<Long, Quiz> quizzes = quizRepository.findAllById(
                        overdue.stream().map(QuizAttempt::getQuizId).distinct().toList())
                .stream().collect(Collectors.toMap(Quiz::getId, Function.identity()));

        overdue.forEach(attempt -> finish(attempt, quizzes.get(attempt.getQuizId()), true));
        return overdue.size();
    }
}
