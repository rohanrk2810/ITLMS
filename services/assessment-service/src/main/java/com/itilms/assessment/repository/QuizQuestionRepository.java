package com.itilms.assessment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.assessment.entity.QuizQuestion;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    /**
     * Every question with its options in one query.
     *
     * <p>Scoring needs all of them, and a twenty-question test would otherwise
     * be twenty-one queries. DISTINCT because the join multiplies rows.
     */
    @Query("""
            SELECT DISTINCT q FROM QuizQuestion q
            LEFT JOIN FETCH q.options
            WHERE q.quizId = :quizId
            ORDER BY q.sequenceNo ASC
            """)
    List<QuizQuestion> findWithOptions(@Param("quizId") Long quizId);

    @Query("""
            SELECT q FROM QuizQuestion q
            LEFT JOIN FETCH q.options
            WHERE q.id = :id
            """)
    Optional<QuizQuestion> findWithOptionsById(@Param("id") Long id);

    List<QuizQuestion> findByQuizIdOrderBySequenceNoAsc(Long quizId);

    long countByQuizId(Long quizId);

    /** Question counts for a page of tests, in one query. */
    @Query("SELECT q.quizId, COUNT(q) FROM QuizQuestion q WHERE q.quizId IN :quizIds GROUP BY q.quizId")
    List<Object[]> countsByQuiz(@Param("quizIds") java.util.Collection<Long> quizIds);

    @Query("SELECT COALESCE(MAX(q.sequenceNo), 0) FROM QuizQuestion q WHERE q.quizId = :quizId")
    int maxSequenceNo(@Param("quizId") Long quizId);
}
