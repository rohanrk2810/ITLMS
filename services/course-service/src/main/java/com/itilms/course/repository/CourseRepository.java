package com.itilms.course.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.course.entity.Course;
import com.itilms.course.entity.CourseStatus;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {

    Optional<Course> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("SELECT COUNT(c) > 0 FROM Course c WHERE lower(c.code) = lower(:code) AND c.id <> :excludeId")
    boolean existsByCodeExcluding(@Param("code") String code, @Param("excludeId") Long excludeId);

    List<Course> findByIdIn(Collection<Long> ids);

    long countByStatus(CourseStatus status);

    /**
     * How many mandatory lessons a course has.
     *
     * <p>The denominator for every progress percentage, and the reason the count
     * is taken live rather than cached on the course: adding a lesson to a
     * running course has to move every enrolled student's percentage down, and a
     * stale denominator would quietly show them as further ahead than they are.
     */
    @Query("""
            SELECT COUNT(l) FROM Lesson l
             WHERE l.mandatory = true
               AND l.moduleId IN (SELECT m.id FROM CourseModule m WHERE m.courseId = :courseId)
            """)
    int countMandatoryLessons(@Param("courseId") Long courseId);

    @Query("""
            SELECT l.id FROM Lesson l
             WHERE l.moduleId IN (SELECT m.id FROM CourseModule m WHERE m.courseId = :courseId)
             ORDER BY l.id
            """)
    List<Long> findLessonIdsByCourse(@Param("courseId") Long courseId);

    /** The course a lesson belongs to, resolved in one hop instead of two. */
    @Query("""
            SELECT m.courseId FROM Lesson l JOIN CourseModule m ON m.id = l.moduleId
             WHERE l.id = :lessonId
            """)
    Optional<Long> findCourseIdByLessonId(@Param("lessonId") Long lessonId);
}
