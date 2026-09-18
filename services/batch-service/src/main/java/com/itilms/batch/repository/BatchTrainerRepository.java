package com.itilms.batch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.batch.entity.BatchTrainer;

@Repository
public interface BatchTrainerRepository extends JpaRepository<BatchTrainer, Long> {

    List<BatchTrainer> findByBatchId(Long batchId);

    void deleteByBatchId(Long batchId);

    boolean existsByBatchIdAndTrainerId(Long batchId, Long trainerId);

    /**
     * Every batch a trainer touches, primary or otherwise.
     *
     * <p>Used by the ownership guard: a co-trainer must be able to mark the
     * register for a session they are teaching, even though the batch names
     * someone else as primary.
     */
    @Query("SELECT bt.batchId FROM BatchTrainer bt WHERE bt.trainerId = :trainerId")
    List<Long> findBatchIdsForTrainer(@Param("trainerId") Long trainerId);
}
