package com.itilms.placement.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.placement.entity.JobOpening;
import com.itilms.placement.entity.JobStatus;

public interface JobOpeningRepository extends JpaRepository<JobOpening, Long> {

    List<JobOpening> findByStatusOrderByPublishedAtDesc(JobStatus status);

    Page<JobOpening> findByStatusOrderByCreatedAtDesc(JobStatus status, Pageable pageable);

    Page<JobOpening> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<JobOpening> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    long countByStatus(JobStatus status);
}
