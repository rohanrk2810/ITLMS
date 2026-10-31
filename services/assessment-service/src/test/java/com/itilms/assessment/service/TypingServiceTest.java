package com.itilms.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.itilms.assessment.dto.request.TypingChunkRequest;
import com.itilms.assessment.dto.response.TypingReplayResponse;
import com.itilms.assessment.entity.AttemptStatus;
import com.itilms.assessment.entity.QuestionType;
import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizAnswer;
import com.itilms.assessment.entity.QuizAttempt;
import com.itilms.assessment.entity.QuizQuestion;
import com.itilms.assessment.entity.QuizStatus;
import com.itilms.assessment.entity.QuizTypingChunk;
import com.itilms.assessment.repository.QuizAnswerRepository;
import com.itilms.assessment.repository.QuizAttemptRepository;
import com.itilms.assessment.repository.QuizQuestionRepository;
import com.itilms.assessment.repository.QuizRepository;
import com.itilms.assessment.repository.QuizTypingChunkRepository;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;

/** Typing replay: only a test that asks for it keeps anything, and only from the student who is sitting it. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TypingServiceTest {

    private static final long STUDENT = 3L;
    private static final String EDITS = "[[120,0,0,\"int\",0],[900,3,0,\" main\",1]]";

    @Mock private QuizRepository quizRepository;
    @Mock private QuizAttemptRepository attemptRepository;
    @Mock private QuizQuestionRepository questionRepository;
    @Mock private QuizAnswerRepository answerRepository;
    @Mock private QuizTypingChunkRepository chunks;
    @Mock private TypingStatsStore typingStats;
    @Mock private AssessmentAccess access;

    private TypingService service;
    private Quiz quiz;
    private QuizAttempt attempt;

    @BeforeEach
    void setUp() {
        service = new TypingService(quizRepository, attemptRepository, questionRepository, answerRepository, chunks, typingStats, access);
        quiz = Quiz.builder().id(1L).courseId(1L).title("t").durationMinutes(30).status(QuizStatus.PUBLISHED)
                .recordTyping(true).build();
        attempt = QuizAttempt.builder().id(9L).quizId(1L).studentId(STUDENT).attemptNo(1)
                .startedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(1800))
                .status(AttemptStatus.IN_PROGRESS).build();
        QuizQuestion coding = QuizQuestion.builder().id(10L).quizId(1L).type(QuestionType.CODING).marks(5)
                .sequenceNo(1).questionText("Sum two numbers").starterCode("class A {}").build();

        when(access.requireStudent()).thenReturn(new AppPrincipal(30L, "s@x", "S", "STUDENT", STUDENT));
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(attemptRepository.findById(9L)).thenReturn(Optional.of(attempt));
        when(questionRepository.findById(10L)).thenReturn(Optional.of(coding));
    }

    @Test
    void aBatchOfEditsIsKept() {
        service.record(9L, 10L, new TypingChunkRequest(0, EDITS));

        ArgumentCaptor<QuizTypingChunk> saved = ArgumentCaptor.forClass(QuizTypingChunk.class);
        verify(chunks).save(saved.capture());
        assertThat(saved.getValue().getAttemptId()).isEqualTo(9L);
        assertThat(saved.getValue().getQuestionId()).isEqualTo(10L);
        assertThat(saved.getValue().getEvents()).isEqualTo(EDITS);
    }

    @Test
    void aTestThatDoesNotRecordKeepsNothing() {
        quiz.setRecordTyping(false);

        service.record(9L, 10L, new TypingChunkRequest(0, EDITS));

        verify(chunks, never()).save(any());
    }

    @Test
    void anAttemptThatIsOverKeepsNothing() {
        attempt.setStatus(AttemptStatus.SUBMITTED);

        service.record(9L, 10L, new TypingChunkRequest(0, EDITS));

        verify(chunks, never()).save(any());
    }

    @Test
    void aQuestionThatIsNotACodingQuestionOfTheTestKeepsNothing() {
        QuizQuestion choice = QuizQuestion.builder().id(11L).quizId(1L).type(QuestionType.SINGLE_CHOICE).marks(1)
                .sequenceNo(2).questionText("q").build();
        QuizQuestion elsewhere = QuizQuestion.builder().id(12L).quizId(2L).type(QuestionType.CODING).marks(1)
                .sequenceNo(1).questionText("q").build();
        when(questionRepository.findById(11L)).thenReturn(Optional.of(choice));
        when(questionRepository.findById(12L)).thenReturn(Optional.of(elsewhere));

        service.record(9L, 11L, new TypingChunkRequest(0, EDITS));
        service.record(9L, 12L, new TypingChunkRequest(0, EDITS));

        verify(chunks, never()).save(any());
    }

    @Test
    void aBatchSentTwiceIsKeptOnce() {
        when(chunks.existsByAttemptIdAndQuestionIdAndSeq(9L, 10L, 0)).thenReturn(true);

        service.record(9L, 10L, new TypingChunkRequest(0, EDITS));

        verify(chunks, never()).save(any());
    }

    @Test
    void anAttemptThatHasStoredEnoughKeepsNoMore() {
        when(chunks.storedSize(9L)).thenReturn(TypingService.MAX_STORED_PER_ATTEMPT);

        service.record(9L, 10L, new TypingChunkRequest(0, EDITS));

        verify(chunks, never()).save(any());
    }

    @Test
    void editsThatAreNotAJsonListAreRefused() {
        assertThatThrownBy(() -> service.record(9L, 10L, new TypingChunkRequest(0, "{\"a\":1}")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.record(9L, 10L, new TypingChunkRequest(0, "not json")))
                .isInstanceOf(BusinessRuleException.class);
        verify(chunks, never()).save(any());
    }

    @Test
    void someoneElsesAttemptLooksLikeItDoesNotExist() {
        attempt.setStudentId(99L);

        assertThatThrownBy(() -> service.record(9L, 10L, new TypingChunkRequest(0, EDITS)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(chunks, never()).save(any());
    }

    @Test
    void theTrainerGetsTheStartingCodeTheEditsInOrderAndTheFinalAnswer() {
        QuizAnswer answer = QuizAnswer.builder().attemptId(9L).questionId(10L).answerText("class A { int x; }").build();
        when(answerRepository.findByAttemptId(9L)).thenReturn(List.of(answer));
        when(chunks.findByAttemptIdAndQuestionIdOrderBySeqAsc(9L, 10L)).thenReturn(List.of(
                QuizTypingChunk.builder().attemptId(9L).questionId(10L).seq(0).events(EDITS).build(),
                QuizTypingChunk.builder().attemptId(9L).questionId(10L).seq(1).events("[]").build()));

        TypingReplayResponse replay = service.replay(9L, 10L);

        verify(access).requireManagesQuiz(quiz);
        assertThat(replay.starterCode()).isEqualTo("class A {}");
        assertThat(replay.finalCode()).isEqualTo("class A { int x; }");
        assertThat(replay.chunks()).extracting(TypingReplayResponse.Chunk::seq).containsExactly(0, 1);
        assertThat(replay.questionText()).isEqualTo("Sum two numbers");
    }
}
