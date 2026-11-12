package com.itilms.finance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.finance.entity.UpiClaim;

public interface UpiClaimRepository extends JpaRepository<UpiClaim, Long> {

    List<UpiClaim> findAllByOrderByIdDesc();

    List<UpiClaim> findByStudentIdOrderByIdDesc(Long studentId);

    boolean existsByUtrAndStatusNot(String utr, UpiClaim.Status status);
}
