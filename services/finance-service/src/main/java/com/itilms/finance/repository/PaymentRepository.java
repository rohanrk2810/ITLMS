package com.itilms.finance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.PaymentMethod;
import com.itilms.finance.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Held for the transaction, so two people reversing the same payment cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> lockById(@Param("id") Long id);

    List<Payment> findByFeePlanIdOrderByPaymentDateAscIdAsc(Long feePlanId);

    List<Payment> findByFeePlanIdIn(Collection<Long> feePlanIds);

    List<Payment> findByStudentIdOrderByPaymentDateDescIdDesc(Long studentId);

    Page<Payment> findByPaymentDateBetweenOrderByPaymentDateDescIdDesc(LocalDate from, LocalDate to, Pageable pageable);

    boolean existsByMethodAndReferenceNoAndStatus(PaymentMethod method, String referenceNo, PaymentStatus status);

    long countByFeePlanIdAndStatus(Long feePlanId, PaymentStatus status);

    /** The next receipt number, from a database sequence so two cashiers never share one. */
    @Query(value = "SELECT nextval('receipt_seq')", nativeQuery = true)
    long nextReceiptSequence();

    /** Successful money received in a period, by method (Doc S15: "payment method split"). */
    @Query("""
            SELECT p.method, COUNT(p), SUM(p.amount)
            FROM Payment p
            WHERE p.status = com.itilms.finance.entity.PaymentStatus.SUCCESS
              AND p.paymentDate BETWEEN :from AND :to
            GROUP BY p.method
            """)
    List<Object[]> methodSplit(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.status = com.itilms.finance.entity.PaymentStatus.SUCCESS
            """)
    BigDecimal totalCollected();
}
