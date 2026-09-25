package com.itilms.assessment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.assessment.entity.QuizViolation;

public interface QuizViolationRepository extends JpaRepository<QuizViolation, Long> {

    List<QuizViolation> findByAttemptIdOrderByOccurredAtAsc(Long attemptId);

    long countByAttemptId(Long attemptId);

    /** The last event that counted, to fold a second report of the same leaving into the first. */
    Optional<QuizViolation> findFirstByAttemptIdAndCountedTrueOrderByOccurredAtDesc(Long attemptId);
}
