package com.itilms.finance.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.finance.entity.Refund;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findAllByOrderByIdDesc();

    List<Refund> findByStatusOrderByIdDesc(Refund.Status status);

    /** Cash handed back on a day: it leaves the drawer, so the day-end count expects that much less. */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.status = com.itilms.finance.entity.Refund$Status.PAID "
            + "AND r.payMethod = 'CASH' AND r.paidOn = :day")
    BigDecimal cashPaidOutOn(@Param("day") java.time.LocalDate day);

    /** Money already asked back on a payment and not yet decided, so two requests cannot add up to more than was paid. */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.paymentId = :paymentId "
            + "AND r.status = com.itilms.finance.entity.Refund$Status.REQUESTED")
    BigDecimal pendingForPayment(@Param("paymentId") Long paymentId);
}
