package com.itilms.finance.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.itilms.finance.entity.Payslip;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    List<Payslip> findByMonthKeyOrderByStaffNameAsc(String monthKey);

    List<Payslip> findByMonthKeyStartingWithOrderByMonthKeyAsc(String yearPrefix);

    List<Payslip> findByUserIdAndStatusOrderByMonthKeyDesc(Long userId, Payslip.Status status);

    Optional<Payslip> findByUserIdAndMonthKey(Long userId, String monthKey);

    @Query(value = "SELECT nextval('payslip_seq')", nativeQuery = true)
    long nextSequence();
}
