package com.itilms.finance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.finance.entity.StaffPayHead;

public interface StaffPayHeadRepository extends JpaRepository<StaffPayHead, Long> {

    List<StaffPayHead> findByUserIdOrderBySortOrderAscIdAsc(Long userId);

    void deleteByUserId(Long userId);
}
