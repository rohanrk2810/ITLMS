package com.itilms.finance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.finance.entity.StaffPayHistory;

public interface StaffPayHistoryRepository extends JpaRepository<StaffPayHistory, Long> {

    List<StaffPayHistory> findByUserIdOrderByIdDesc(Long userId);
}
