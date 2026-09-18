package com.itilms.finance.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.Roles;
import com.itilms.finance.dto.request.ReasonRequest;
import com.itilms.finance.dto.request.RecordPaymentRequest;
import com.itilms.finance.dto.response.PaymentResponse;
import com.itilms.finance.dto.response.ReceiptResponse;
import com.itilms.finance.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Payments and receipts (Doc S6.12, S11). */
@Tag(name = "Payments", description = "Recording money received, receipts and reversals")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Record a payment",
            description = "Applied to the plan's installments oldest first, and given a receipt number. "
                    + "Non-cash payments need their transaction or reference number, and the same "
                    + "reference cannot be recorded twice.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Recorded"),
            @ApiResponse(responseCode = "409", description = "That reference is already recorded"),
            @ApiResponse(responseCode = "422", description = "More than is outstanding, or the plan is cancelled")
    })
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping
    public ResponseEntity<PaymentResponse> record(@Valid @RequestBody RecordPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.record(request));
    }

    @Operation(summary = "Payments in a date range", description = "Defaults to this month.")
    @PreAuthorize(Roles.FINANCE_VIEW)
    @GetMapping
    public PageResponse<PaymentResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 50) Pageable pageable) {
        return paymentService.list(from, to, pageable);
    }

    @Operation(summary = "One payment")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','COORDINATOR','STUDENT')")
    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable Long id) {
        return paymentService.get(id);
    }

    @Operation(summary = "A payment's receipt", description = "A student may fetch only their own.")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','COORDINATOR','STUDENT')")
    @GetMapping("/{id}/receipt")
    public ReceiptResponse receipt(@PathVariable Long id) {
        return paymentService.receipt(id);
    }

    @Operation(summary = "Reverse a payment",
            description = "For a bounced cheque or a mistaken entry. The payment stays on record, marked "
                    + "reversed; the student is told and the change is audited.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping("/{id}/reverse")
    public PaymentResponse reverse(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return paymentService.reverse(id, request.reason());
    }
}
