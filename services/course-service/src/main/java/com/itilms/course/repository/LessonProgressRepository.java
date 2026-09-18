package com.itilms.course.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.course.entity.LessonProgress;

@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    Optional<LessonProgress> findByEnrollmentIdAndLessonId(Long enrollmentId, Long lessonId);

    List<LessonProgress> findByEnrollmentId(Long enrollmentId);

    /**
     * How many of the given lessons this enrolment has finished.
     *
     * <p>Scoped to an explicit lesson-id list rather than counting every
     * completed row, because only mandatory lessons count toward completion.
     * Counting all of them would let a student reach 100% by watching the
     * optional extras.
     */
    @Query("""
            SELECT COUNT(p) FROM LessonProgress p
             WHERE p.enrollmentId = :enrollmentId
               AND p.completed = true
               AND p.lessonId IN :lessonIds
            """)
    int countCompletedAmong(@Param("enrollmentId") Long enrollmentId,
                            @Param("lessonIds") Collection<Long> lessonIds);

    @Query("SELECT p.lessonId FROM LessonProgress p WHERE p.enrollmentId = :enrollmentId AND p.completed = true")
    List<Long> findCompletedLessonIds(@Param("enrollmentId") Long enrollmentId);

    /**
     * How many students have finished a given lesson.
     *
     * <p>Guards lesson deletion. Deliberately a COUNT rather than loading rows:
     * this is checked on every delete, and a mature institute has progress rows
     * numbering in the hundreds of thousands.
     */
    long countByLessonIdAndCompletedTrue(Long lessonId);

    /** Same guard, for every lesson in a module, in one query. */
    @Query("""
            SELECT COUNT(p) FROM LessonProgress p
             WHERE p.completed = true
               AND p.lessonId IN :lessonIds
            """)
    long countCompletionsForLessons(@Param("lessonIds") Collection<Long> lessonIds);

    void deleteByEnrollmentId(Long enrollmentId);
}
