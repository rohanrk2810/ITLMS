package com.itilms.assessment.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.assessment.entity.AttemptStatus;
import com.itilms.assessment.entity.QuizAttempt;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    long countByQuizIdAndStudentId(Long quizId, Long studentId);

    /** The sitting a student is in the middle of, if any. */
    Optional<QuizAttempt> findFirstByQuizIdAndStudentIdAndStatusOrderByAttemptNoDesc(
            Long quizId, Long studentId, AttemptStatus status);

    List<QuizAttempt> findByQuizIdAndStudentIdOrderByAttemptNoDesc(Long quizId, Long studentId);

    List<QuizAttempt> findByStudentIdOrderByStartedAtDesc(Long studentId);

    List<QuizAttempt> findByStudentIdAndQuizIdIn(Long studentId, Collection<Long> quizIds);

    List<QuizAttempt> findByQuizIdAndStatusInOrderByScoreDesc(Long quizId, Collection<AttemptStatus> statuses);

    @Query("SELECT COALESCE(MAX(a.attemptNo), 0) FROM QuizAttempt a WHERE a.quizId = :quizId AND a.studentId = :studentId")
    int lastAttemptNo(@Param("quizId") Long quizId, @Param("studentId") Long studentId);

    /**
     * Sittings whose time has run out but which were never submitted.
     *
     * <p>Closing a browser tab is the commonest way a test ends. Without this
     * sweep the attempt stays open for ever, the student's remaining attempts
     * are used up by a sitting with no score, and nothing reaches the register.
     */
    @Query("""
            SELECT a FROM QuizAttempt a
            WHERE a.status = com.itilms.assessment.entity.AttemptStatus.IN_PROGRESS
              AND a.expiresAt < :now
            ORDER BY a.expiresAt ASC
            """)
    List<QuizAttempt> findExpired(@Param("now") Instant now);

    /**
     * A student's best score per test - what a result screen and the
     * certificate check both want (Doc S7.3).
     */
    @Query("""
            SELECT a.quizId, MAX(a.percentage)
            FROM QuizAttempt a
            WHERE a.studentId = :studentId
              AND a.quizId IN :quizIds
              AND a.status <> com.itilms.assessment.entity.AttemptStatus.IN_PROGRESS
            GROUP BY a.quizId
            """)
    List<Object[]> bestPercentages(@Param("studentId") Long studentId,
                                   @Param("quizIds") Collection<Long> quizIds);
}
