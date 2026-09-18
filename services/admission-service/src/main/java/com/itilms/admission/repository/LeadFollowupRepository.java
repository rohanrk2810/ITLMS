package com.itilms.admission.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.itilms.admission.entity.LeadFollowup;

@Repository
public interface LeadFollowupRepository extends JpaRepository<LeadFollowup, Long> {

    List<LeadFollowup> findByLeadIdOrderByContactedAtDesc(Long leadId);

    long countByLeadId(Long leadId);
}
