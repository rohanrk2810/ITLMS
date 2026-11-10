package com.itilms.finance.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.finance.entity.OnlineOrder;

import jakarta.persistence.LockModeType;

public interface OnlineOrderRepository extends JpaRepository<OnlineOrder, Long> {

    Optional<OnlineOrder> findByRazorpayOrderId(String razorpayOrderId);

    /** Held for the transaction, so a double click or a retry cannot record the payment twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OnlineOrder o WHERE o.razorpayOrderId = :id")
    Optional<OnlineOrder> lockByRazorpayOrderId(@Param("id") String razorpayOrderId);
}
