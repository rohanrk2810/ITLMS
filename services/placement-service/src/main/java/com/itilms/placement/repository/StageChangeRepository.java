package com.itilms.placement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.placement.entity.StageChange;

public interface StageChangeRepository extends JpaRepository<StageChange, Long> {

    List<StageChange> findByApplicationIdOrderByChangedAtAsc(Long applicationId);
}
