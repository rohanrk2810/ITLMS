package com.itilms.course.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.course.entity.CourseEnrollment;

@Repository
public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {

    Optional<CourseEnrollment> findByEnrollmentId(Long enrollmentId);

    boolean existsByEnrollmentId(Long enrollmentId);

    List<CourseEnrollment> findByStudentId(Long studentId);

    Optional<CourseEnrollment> findByStudentIdAndCourseId(Long studentId, Long courseId);

    List<CourseEnrollment> findByBatchId(Long batchId);

    /**
     * Every enrolment on a course.
     *
     * <p>Used when the curriculum changes: adding or removing a mandatory lesson
     * moves the denominator, so all affected students need their percentage
     * recomputed rather than being left with a figure derived from a curriculum
     * that no longer exists.
     */
    @Query("SELECT e FROM CourseEnrollment e WHERE e.courseId = :courseId AND e.status = 'ACTIVE'")
    List<CourseEnrollment> findActiveByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT AVG(e.progressPercent) FROM CourseEnrollment e
             WHERE e.batchId = :batchId AND e.status = 'ACTIVE'
            """)
    Double averageProgressForBatch(@Param("batchId") Long batchId);
}
