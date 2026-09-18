package com.itilms.admission.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.admission.entity.Trainer;
import com.itilms.admission.entity.TrainerStatus;

@Repository
public interface TrainerRepository extends JpaRepository<Trainer, Long>, JpaSpecificationExecutor<Trainer> {

    Optional<Trainer> findByUserId(Long userId);

    Optional<Trainer> findByEmployeeCode(String employeeCode);

    boolean existsByUserId(Long userId);

    List<Trainer> findByIdIn(Collection<Long> ids);

    List<Trainer> findByStatus(TrainerStatus status);

    long countByStatus(TrainerStatus status);

    @Query("SELECT COUNT(t) + 1 FROM Trainer t")
    long nextCodeSequence();

    /**
     * Trainers cleared to teach a course and currently available.
     *
     * <p>This is what populates the trainer dropdown when a coordinator creates
     * a batch, so it filters on both qualification and availability — offering
     * someone who is on leave would only produce a rejected allocation.
     */
    @Query("""
            SELECT t FROM Trainer t
             WHERE t.status = 'ACTIVE'
               AND EXISTS (SELECT 1 FROM TrainerCourse tc
                            WHERE tc.trainerId = t.id AND tc.courseId = :courseId)
             ORDER BY t.fullName
            """)
    List<Trainer> findAvailableForCourse(@Param("courseId") Long courseId);

    @Modifying
    @Query("""
            UPDATE Trainer t
               SET t.fullName = :fullName, t.email = :email, t.phone = :phone
             WHERE t.userId = :userId
            """)
    int syncIdentity(@Param("userId") Long userId,
                     @Param("fullName") String fullName,
                     @Param("email") String email,
                     @Param("phone") String phone);
}
