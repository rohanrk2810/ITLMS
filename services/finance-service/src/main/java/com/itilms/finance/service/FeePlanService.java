package com.itilms.finance.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.common.event.StudentAdmittedEvent;
import com.itilms.finance.dto.request.ChargeDtos.BatchChargeRequest;
import com.itilms.finance.dto.request.ChargeDtos.BatchChargeResult;
import com.itilms.finance.dto.request.ChargeDtos.ChargeRequest;
import com.itilms.finance.dto.request.FeePlanRequest;
import com.itilms.finance.dto.response.FeePlanResponse;

/** Fee plans and their schedules (Doc S6.12). */
public interface FeePlanService {

    /**
     * Raises the plan agreed at admission (Doc S7.1: "Admission Confirmed ->
     * ... -> Fee Plan Created").
     *
     * <p>Idempotent: a redelivered event for a student who already has a plan
     * for the course does nothing.
     */
    void createFromAdmission(StudentAdmittedEvent event);

    /**
     * Raises the course fee as a fee plan when the student is placed in a batch, so what they owe shows on their
     * Fees page before any payment. Does nothing when a plan already exists (it only records the batch), or when the
     * course has no fee. Idempotent.
     */
    void createFromEnrollment(EnrollmentCreatedEvent event);

    /** Adds a named extra charge (a development fee, a book) to one plan. */
    FeePlanResponse addCharge(Long planId, ChargeRequest request);

    /** Adds the same charge to the plan of every student in a batch. */
    BatchChargeResult addChargeToBatch(BatchChargeRequest request);

    FeePlanResponse create(FeePlanRequest request);

    /**
     * Changes the fee, discount or schedule.
     *
     * <p>The net fee cannot drop below what the student has already paid;
     * that would need a refund, which is outside the MVP (Doc S3.2).
     */
    FeePlanResponse update(Long id, FeePlanRequest request);

    /** Withdraws a plan with no payments on it - a student who never joined. */
    FeePlanResponse cancel(Long id, String reason);

    FeePlanResponse get(Long id);

    List<FeePlanResponse> forStudent(Long studentId);

    /** Doc S11: GET /api/fees/me. */
    List<FeePlanResponse> mine();

    PageResponse<FeePlanResponse> list(String status, Pageable pageable);
}
