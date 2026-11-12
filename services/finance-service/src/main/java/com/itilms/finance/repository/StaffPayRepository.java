package com.itilms.finance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.finance.entity.StaffPay;

public interface StaffPayRepository extends JpaRepository<StaffPay, Long> {

    List<StaffPay> findByActiveTrue();
}
