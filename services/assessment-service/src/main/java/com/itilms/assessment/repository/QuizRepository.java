package com.itilms.assessment.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.assessment.entity.Quiz;
import com.itilms.assessment.entity.QuizStatus;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    Page<Quiz> findByCourseIdOrderByCreatedAtDesc(Long courseId, Pageable pageable);

    Page<Quiz> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByCourseIdAndStatus(Long courseId, QuizStatus status);

    /**
     * Tests a student may take: published, for a course they study, and either
     * set for the whole course or for one of their own batches.
     *
     * <p>The batch condition is what stops a test written for the weekend group
     * appearing to the weekday group.
     */
    @Query("""
            SELECT q FROM Quiz q
            WHERE q.status = com.itilms.assessment.entity.QuizStatus.PUBLISHED
              AND q.courseId IN :courseIds
              AND (q.batchId IS NULL OR q.batchId IN :batchIds)
            ORDER BY q.availableUntil ASC NULLS LAST, q.createdAt DESC
            """)
    List<Quiz> findAvailableFor(@Param("courseIds") Collection<Long> courseIds,
                                @Param("batchIds") Collection<Long> batchIds);

    /** Mandatory tests for a course - the completion check in Doc S7.3. */
    @Query("""
            SELECT q FROM Quiz q
            WHERE q.courseId = :courseId
              AND q.mandatory = true
              AND q.status <> com.itilms.assessment.entity.QuizStatus.DRAFT
            """)
    List<Quiz> findMandatoryForCourse(@Param("courseId") Long courseId);

    /**
     * Keeps the stored total in step with the questions.
     *
     * <p>A single statement rather than loading the quiz and its questions:
     * this runs after every question is added, edited or removed.
     *
     * <p>Flushes first, so a question saved a moment ago is in the sum, and
     * clears afterwards, so the next read of this quiz sees the new total
     * rather than the copy Hibernate cached before the update.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Quiz q SET q.totalMarks =
                COALESCE((SELECT SUM(x.marks) FROM QuizQuestion x WHERE x.quizId = :quizId), 0)
            WHERE q.id = :quizId
            """)
    void refreshTotalMarks(@Param("quizId") Long quizId);

    /** Mandatory tests a student of this batch must pass: the course-wide ones and the batch's own. */
    @Query("""
            SELECT q FROM Quiz q
            WHERE q.courseId = :courseId
              AND q.mandatory = true
              AND q.status <> com.itilms.assessment.entity.QuizStatus.DRAFT
              AND (q.batchId IS NULL OR q.batchId = :batchId)
            """)
    List<Quiz> findMandatoryForBatch(@Param("courseId") Long courseId, @Param("batchId") Long batchId);
}
