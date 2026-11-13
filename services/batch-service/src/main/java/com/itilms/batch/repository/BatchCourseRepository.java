package com.itilms.batch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.batch.entity.BatchCourse;

@Repository
public interface BatchCourseRepository extends JpaRepository<BatchCourse, Long> {

    List<BatchCourse> findByBatchIdOrderByAddedAt(Long batchId);

    Optional<BatchCourse> findByBatchIdAndCourseId(Long batchId, Long courseId);

    boolean existsByBatchIdAndCourseId(Long batchId, Long courseId);

    /** Batches where this trainer teaches one of the added courses (so they may mark those registers). */
    @Query("SELECT bc.batchId FROM BatchCourse bc WHERE bc.trainerId = :trainerId")
    List<Long> findBatchIdsForTrainer(@Param("trainerId") Long trainerId);

    @Query("SELECT bc FROM BatchCourse bc WHERE bc.batchId IN :batchIds")
    List<BatchCourse> findByBatchIdIn(@Param("batchIds") java.util.Collection<Long> batchIds);
}
