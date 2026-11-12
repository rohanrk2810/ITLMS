package com.itilms.finance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.finance.entity.CashClosing;

public interface CashClosingRepository extends JpaRepository<CashClosing, Long> {

    Optional<CashClosing> findByClosingDate(LocalDate date);

    List<CashClosing> findTop30ByOrderByClosingDateDesc();
}
