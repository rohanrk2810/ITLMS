package com.itilms.course.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.course.entity.Lesson;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByModuleIdOrderBySequenceNo(Long moduleId);

    List<Lesson> findByModuleIdInOrderByModuleIdAscSequenceNoAsc(Collection<Long> moduleIds);

    long countByModuleId(Long moduleId);

    @Query("SELECT COALESCE(MAX(l.sequenceNo), 0) + 1 FROM Lesson l WHERE l.moduleId = :moduleId")
    int nextSequenceNo(@Param("moduleId") Long moduleId);

    /** Preview lessons, which the public catalog may show without enrolment. */
    @Query("""
            SELECT l FROM Lesson l
             WHERE l.preview = true
               AND l.moduleId IN (SELECT m.id FROM CourseModule m WHERE m.courseId = :courseId)
             ORDER BY l.moduleId, l.sequenceNo
            """)
    List<Lesson> findPreviewLessons(@Param("courseId") Long courseId);

    /**
     * Mandatory lesson ids for a course.
     *
     * <p>Used to decide "has this student finished everything required", which
     * is one of the four certificate conditions in Doc S7.3.
     */
    @Query("""
            SELECT l.id FROM Lesson l
             WHERE l.mandatory = true
               AND l.moduleId IN (SELECT m.id FROM CourseModule m WHERE m.courseId = :courseId)
            """)
    List<Long> findMandatoryLessonIds(@Param("courseId") Long courseId);
}
