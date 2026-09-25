package com.itilms.batch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.batch.entity.CourseRequest;
import com.itilms.batch.entity.CourseRequestStatus;

import jakarta.persistence.LockModeType;

public interface CourseRequestRepository extends JpaRepository<CourseRequest, Long> {

    /** Held for the rest of the transaction, so two reviewers cannot decide the same request at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CourseRequest r WHERE r.id = :id")
    Optional<CourseRequest> findByIdForUpdate(@Param("id") Long id);

    boolean existsByStudentIdAndCourseIdAndStatus(Long studentId, Long courseId, CourseRequestStatus status);

    List<CourseRequest> findByStudentIdOrderByIdDesc(Long studentId);

    Page<CourseRequest> findByStatusOrderByIdDesc(CourseRequestStatus status, Pageable pageable);

    Page<CourseRequest> findAllByOrderByIdDesc(Pageable pageable);
}
