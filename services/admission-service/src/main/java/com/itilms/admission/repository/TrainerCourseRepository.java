package com.itilms.admission.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.admission.entity.TrainerCourse;

@Repository
public interface TrainerCourseRepository extends JpaRepository<TrainerCourse, Long> {

    List<TrainerCourse> findByTrainerId(Long trainerId);

    void deleteByTrainerId(Long trainerId);

    @Query("SELECT tc.courseId FROM TrainerCourse tc WHERE tc.trainerId = :trainerId")
    List<Long> findCourseIdsByTrainerId(@Param("trainerId") Long trainerId);

    boolean existsByTrainerIdAndCourseId(Long trainerId, Long courseId);
}
