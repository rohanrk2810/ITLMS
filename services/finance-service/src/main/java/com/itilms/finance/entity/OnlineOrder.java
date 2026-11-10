package com.itilms.finance.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.itilms.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One Razorpay checkout for one course. The amount is fixed here when the order is created. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "online_orders")
public class OnlineOrder extends AuditableEntity {

    public enum Status { CREATED, PAID, ENROLLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "razorpay_order_id", nullable = false, length = 80)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 80)
    private String razorpayPaymentId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "student_user_id")
    private Long studentUserId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "course_title", length = 200)
    private String courseTitle;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.CREATED;

    @Column(name = "fee_plan_id")
    private Long feePlanId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "paid_at")
    private Instant paidAt;
}
