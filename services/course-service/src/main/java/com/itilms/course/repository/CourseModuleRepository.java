package com.itilms.course.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.course.entity.CourseModule;

@Repository
public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {

    List<CourseModule> findByCourseIdOrderBySequenceNo(Long courseId);

    long countByCourseId(Long courseId);

    Optional<CourseModule> findByCourseIdAndSequenceNo(Long courseId, Integer sequenceNo);

    /** Next free position, so a new module lands at the end of the curriculum. */
    @Query("SELECT COALESCE(MAX(m.sequenceNo), 0) + 1 FROM CourseModule m WHERE m.courseId = :courseId")
    int nextSequenceNo(@Param("courseId") Long courseId);

    @Query("SELECT m.id FROM CourseModule m WHERE m.courseId = :courseId")
    List<Long> findIdsByCourseId(@Param("courseId") Long courseId);
}
