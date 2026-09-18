package com.itilms.finance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.FeePlanStatus;

public interface FeePlanRepository extends JpaRepository<FeePlan, Long> {

    @EntityGraph(attributePaths = "installments")
    Optional<FeePlan> findWithInstallmentsById(Long id);

    /**
     * Loads a plan and holds its row until the transaction ends.
     *
     * <p>Two cashiers taking money for the same student at the same moment
     * would otherwise both check the balance before either had saved, and
     * together accept more than is owed. Locking the plan makes the second
     * wait, then see the first payment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM FeePlan p WHERE p.id = :id")
    Optional<FeePlan> lockById(@Param("id") Long id);

    @EntityGraph(attributePaths = "installments")
    List<FeePlan> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    boolean existsByStudentIdAndCourseIdAndStatusNot(Long studentId, Long courseId, FeePlanStatus status);

    Page<FeePlan> findByStatusOrderByCreatedAtDesc(FeePlanStatus status, Pageable pageable);

    Page<FeePlan> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Every plan still owed on, with its schedule - the dashboard's and the overdue list's input. */
    @EntityGraph(attributePaths = "installments")
    List<FeePlan> findByStatus(FeePlanStatus status);

    /**
     * Active plans with an installment due on or before a date.
     *
     * <p>The daily reminder scan's query. Plans whose next installment is
     * months away are not loaded at all.
     */
    @Query("""
            SELECT DISTINCT p FROM FeePlan p
            JOIN FETCH p.installments
            WHERE p.status = com.itilms.finance.entity.FeePlanStatus.ACTIVE
              AND p.id IN (SELECT q.id FROM FeePlan q JOIN q.installments x WHERE x.dueDate <= :until)
            """)
    List<FeePlan> findActiveWithInstallmentDueBy(@Param("until") LocalDate until);

    long countByStatus(FeePlanStatus status);

    /** Doc S15 "total billed": the net fee on every plan that was not withdrawn. */
    @Query("""
            SELECT COALESCE(SUM(p.netFee), 0) FROM FeePlan p
            WHERE p.status <> com.itilms.finance.entity.FeePlanStatus.CANCELLED
            """)
    java.math.BigDecimal totalBilled();
}
