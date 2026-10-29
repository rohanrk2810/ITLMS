package com.itilms.assessment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.assessment.entity.QuizTypingChunk;

public interface QuizTypingChunkRepository extends JpaRepository<QuizTypingChunk, Long> {

    List<QuizTypingChunk> findByAttemptIdAndQuestionIdOrderBySeqAsc(Long attemptId, Long questionId);

    boolean existsByAttemptIdAndQuestionIdAndSeq(Long attemptId, Long questionId, int seq);

    /** How much has been kept for an attempt, in characters, to cap what one browser can store. */
    @Query("SELECT COALESCE(SUM(LENGTH(c.events)), 0) FROM QuizTypingChunk c WHERE c.attemptId = :attemptId")
    long storedSize(@Param("attemptId") Long attemptId);
}
