package com.itilms.liveclass.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.liveclass.entity.LiveQuestionAnswer;

public interface LiveQuestionAnswerRepository extends JpaRepository<LiveQuestionAnswer, Long> {

    Optional<LiveQuestionAnswer> findByQuestionIdAndUserId(Long questionId, Long userId);

    List<LiveQuestionAnswer> findByQuestionIdOrderBySubmittedAtAsc(Long questionId);

    List<LiveQuestionAnswer> findByUserIdAndQuestionIdIn(Long userId, Collection<Long> questionIds);

    List<LiveQuestionAnswer> findByQuestionIdIn(Collection<Long> questionIds);
}
