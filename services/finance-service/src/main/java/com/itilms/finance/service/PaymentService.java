package com.itilms.finance.service;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;

import com.itilms.common.dto.PageResponse;
import com.itilms.finance.dto.request.RecordPaymentRequest;
import com.itilms.finance.dto.response.PaymentResponse;
import com.itilms.finance.dto.response.ReceiptResponse;

/** Money received (Doc S6.12). */
public interface PaymentService {

    /** Doc S11: POST /api/payments. Issues a receipt number. */
    PaymentResponse record(RecordPaymentRequest request);

    /**
     * Undoes a payment - a bounced cheque, a charge-back, an entry made by
     * mistake. The payment and its receipt stay on record, marked reversed.
     */
    PaymentResponse reverse(Long id, String reason);

    PaymentResponse get(Long id);

    ReceiptResponse receipt(Long id);

    PageResponse<PaymentResponse> list(LocalDate from, LocalDate to, Pageable pageable);
}
