package com.itilms.finance.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.finance.entity.BankLine;

public interface BankLineRepository extends JpaRepository<BankLine, Long> {

    List<BankLine> findAllByOrderByLineDateDescIdDesc();

    List<BankLine> findByMatchedKindIsNull();

    List<BankLine> findByMatchedKindIsNotNull();

    List<BankLine> findByImportKeyIn(Collection<String> keys);
}
