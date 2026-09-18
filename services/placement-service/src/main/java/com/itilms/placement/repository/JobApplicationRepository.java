package com.itilms.placement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.itilms.placement.entity.ApplicationStage;
import com.itilms.placement.entity.JobApplication;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    boolean existsByJobIdAndStudentId(Long jobId, Long studentId);

    Optional<JobApplication> findByJobIdAndStudentId(Long jobId, Long studentId);

    List<JobApplication> findByStudentIdOrderByAppliedAtDesc(Long studentId);

    List<JobApplication> findByJobIdOrderByAppliedAtAsc(Long jobId);

    List<JobApplication> findByStudentIdAndJobIdIn(Long studentId, Collection<Long> jobIds);

    List<JobApplication> findByStageOrderByDecidedAtDesc(ApplicationStage stage);

    long countByJobIdAndStage(Long jobId, ApplicationStage stage);

    long countByJobId(Long jobId);

    /** Application counts for a page of jobs, in one query. */
    @Query("SELECT a.jobId, COUNT(a) FROM JobApplication a WHERE a.jobId IN :jobIds GROUP BY a.jobId")
    List<Object[]> countsByJob(@org.springframework.data.repository.query.Param("jobIds") Collection<Long> jobIds);

    /** Applications per stage across every job - the pipeline row of the dashboard. */
    @Query("SELECT a.stage, COUNT(a) FROM JobApplication a GROUP BY a.stage")
    List<Object[]> countByStage();
}
