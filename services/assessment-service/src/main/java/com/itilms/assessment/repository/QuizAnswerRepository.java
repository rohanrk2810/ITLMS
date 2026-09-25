package com.itilms.assessment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.assessment.entity.QuizAnswer;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {

    List<QuizAnswer> findByAttemptId(Long attemptId);

    List<QuizAnswer> findByAttemptIdIn(java.util.Collection<Long> attemptIds);

    void deleteByAttemptId(Long attemptId);
}
