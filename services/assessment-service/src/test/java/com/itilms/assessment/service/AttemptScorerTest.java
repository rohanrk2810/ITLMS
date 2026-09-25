package com.itilms.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.itilms.assessment.entity.AttemptStatus;
import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizAttempt;
import com.itilms.assessment.repository.QuizAnswerRepository;
import com.itilms.assessment.repository.QuizAttemptRepository;
import com.itilms.assessment.repository.QuizQuestionRepository;
import com.itilms.assessment.repository.QuizRepository;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.QuizAttemptCompletedEvent;

/** How an attempt ends, and what the rest of the system is told about it. */
class AttemptScorerTest {

    private QuizAttemptRepository attempts;
    private EventPublisher events;
    private AttemptScorer scorer;
    private Quiz quiz;
    private QuizAttempt attempt;

    @BeforeEach
    void setUp() {
        attempts = mock(QuizAttemptRepository.class);
        QuizAnswerRepository answers = mock(QuizAnswerRepository.class);
        QuizQuestionRepository questions = mock(QuizQuestionRepository.class);
        events = mock(EventPublisher.class);
        scorer = new AttemptScorer(attempts, answers, questions, mock(QuizRepository.class), events);

        // A pass mark of zero, so that scoring alone would call this attempt a pass.
        quiz = Quiz.builder().id(1L).courseId(2L).title("t").durationMinutes(30).totalMarks(10).passPercentage(0).build();
        attempt = QuizAttempt.builder().id(9L).quizId(1L).studentId(3L).attemptNo(1)
                .expiresAt(Instant.now().plusSeconds(600)).build();
        when(questions.findWithOptions(1L)).thenReturn(List.of());
        when(answers.findByAttemptId(9L)).thenReturn(List.of());
        when(attempts.save(any(QuizAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private QuizAttemptCompletedEvent publishedEvent() {
        ArgumentCaptor<DomainEvent> event = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publishAfterCommit(eq(KafkaTopics.QUIZ_ATTEMPT_COMPLETED), anyString(), event.capture());
        return (QuizAttemptCompletedEvent) event.getValue();
    }

    @Test
    @DisplayName("A terminated attempt is finished and failed, and the completion event says failed")
    void terminatedIsFailedEverywhere() {
        scorer.terminate(attempt, quiz, "Left the test window 2 times (the limit is 2).");

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.TERMINATED);
        assertThat(attempt.getStatus().isFinished()).isTrue();
        assertThat(attempt.getPassed()).isFalse();
        assertThat(attempt.getScore()).isNotNull();   // scored for the record
        assertThat(attempt.getTerminatedReason()).contains("2 times");
        // Progress and certificates listen to this event; a terminated attempt must not look like a pass.
        assertThat(publishedEvent().passed()).isFalse();
    }

    @Test
    @DisplayName("The same attempt submitted normally does pass, so the failure above is the termination's doing")
    void submittedNormallyPasses() {
        scorer.finish(attempt, quiz, false);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(attempt.getPassed()).isTrue();
        assertThat(publishedEvent().passed()).isTrue();
    }

    @Test
    @DisplayName("An attempt that has already ended is not scored or terminated a second time")
    void endedAttemptIsLeftAlone() {
        scorer.finish(attempt, quiz, false);

        scorer.terminate(attempt, quiz, "late report");

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(attempt.getPassed()).isTrue();
        verify(attempts, org.mockito.Mockito.times(1)).save(any());
        verify(events, org.mockito.Mockito.times(1)).publishAfterCommit(anyString(), anyString(), any());
        verify(events, never()).publishNow(anyString(), anyString(), any());
    }
}
