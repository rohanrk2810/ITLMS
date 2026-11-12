package com.itilms.finance.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.finance.entity.GstPayment;

public interface GstPaymentRepository extends JpaRepository<GstPayment, Long> {

    List<GstPayment> findByPaidOnBetweenOrderByPaidOnDescIdDesc(LocalDate from, LocalDate to);

    List<GstPayment> findAllByOrderByPaidOnDescIdDesc();
}
