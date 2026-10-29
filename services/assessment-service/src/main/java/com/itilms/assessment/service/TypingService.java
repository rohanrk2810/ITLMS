package com.itilms.assessment.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.assessment.dto.request.TypingChunkRequest;
import com.itilms.assessment.dto.response.TypingReplayResponse;
import com.itilms.assessment.entity.AttemptStatus;
import com.itilms.assessment.entity.QuestionType;
import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizAnswer;
import com.itilms.assessment.entity.QuizAttempt;
import com.itilms.assessment.entity.QuizQuestion;
import com.itilms.assessment.entity.QuizTypingChunk;
import com.itilms.assessment.repository.QuizAnswerRepository;
import com.itilms.assessment.repository.QuizAttemptRepository;
import com.itilms.assessment.repository.QuizQuestionRepository;
import com.itilms.assessment.repository.QuizRepository;
import com.itilms.assessment.repository.QuizTypingChunkRepository;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;

import lombok.RequiredArgsConstructor;

/**
 * Typing replay: keeps the edits a student's browser reports for a coding answer, and hands them to the trainer.
 *
 * <p>The browser is the one recording, so it can be switched off or fed made-up edits. The value is in the honest
 * majority, and in a sitting whose coding answers arrive with no typing at all.
 */
@Service
@RequiredArgsConstructor
public class TypingService {

    /** What one attempt may keep, in characters of JSON. A browser stuck in a loop must not fill the table. */
    static final long MAX_STORED_PER_ATTEMPT = 3_000_000L;

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizAnswerRepository answerRepository;
    private final QuizTypingChunkRepository chunks;
    private final AssessmentAccess access;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * A student's browser sends a batch of edits. Accepted and ignored when the test does not record typing, the
     * question is not a coding question of it, the attempt is over, or the batch has been seen: there is nothing for
     * the page to handle, since it had every right to send it.
     */
    @Transactional
    public void record(Long attemptId, Long questionId, TypingChunkRequest request) {
        AppPrincipal student = access.requireStudent();
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt", attemptId));
        if (!attempt.getStudentId().equals(student.profileId())) {
            // 404 rather than 403: whether someone else's attempt exists is nothing a student needs to learn.
            throw new ResourceNotFoundException("Attempt", attemptId);
        }
        Quiz quiz = requireQuiz(attempt.getQuizId());
        if (!quiz.isRecordTyping() || attempt.getStatus() != AttemptStatus.IN_PROGRESS
                || attempt.hasExpired(Instant.now()) || !isCodingQuestionOf(quiz, questionId)) {
            return;
        }
        requireEditList(request.events());
        if (chunks.existsByAttemptIdAndQuestionIdAndSeq(attemptId, questionId, request.seq())
                || chunks.storedSize(attemptId) >= MAX_STORED_PER_ATTEMPT) {
            return;
        }
        chunks.save(QuizTypingChunk.builder().attemptId(attemptId).questionId(questionId)
                .seq(request.seq()).events(request.events()).build());
    }

    /** The trainer's view of one coding answer: the starting code, every edit in order, and where it ended. */
    @Transactional(readOnly = true)
    public TypingReplayResponse replay(Long attemptId, Long questionId) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt", attemptId));
        Quiz quiz = requireQuiz(attempt.getQuizId());
        access.requireManagesQuiz(quiz);
        QuizQuestion question = questionRepository.findById(questionId)
                .filter(q -> q.getQuizId().equals(quiz.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Question", questionId));

        String finalCode = answerRepository.findByAttemptId(attemptId).stream()
                .filter(a -> questionId.equals(a.getQuestionId()))
                .map(QuizAnswer::getAnswerText).findFirst().orElse(null);
        List<TypingReplayResponse.Chunk> parts = chunks.findByAttemptIdAndQuestionIdOrderBySeqAsc(attemptId, questionId)
                .stream().map(c -> new TypingReplayResponse.Chunk(c.getSeq(), c.getEvents())).toList();
        return new TypingReplayResponse(questionId, question.getQuestionText(),
                question.getStarterCode() == null ? "" : question.getStarterCode(), finalCode, parts);
    }

    private boolean isCodingQuestionOf(Quiz quiz, Long questionId) {
        return questionRepository.findById(questionId)
                .map(q -> q.getQuizId().equals(quiz.getId()) && q.getType() == QuestionType.CODING)
                .orElse(false);
    }

    private void requireEditList(String events) {
        JsonNode node;
        try {
            node = json.readTree(events);
        } catch (Exception ex) {
            throw new BusinessRuleException("INVALID_TYPING", "The edits are not valid.");
        }
        if (node == null || !node.isArray()) {
            throw new BusinessRuleException("INVALID_TYPING", "The edits must be a list.");
        }
    }

    private Quiz requireQuiz(Long id) {
        return quizRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Test", id));
    }
}
