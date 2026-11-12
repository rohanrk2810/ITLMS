package com.itilms.finance.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.finance.entity.PayslipItem;

public interface PayslipItemRepository extends JpaRepository<PayslipItem, Long> {

    List<PayslipItem> findBySlipIdInOrderBySortOrderAscIdAsc(Collection<Long> slipIds);

    List<PayslipItem> findBySlipIdOrderBySortOrderAscIdAsc(Long slipId);
}
