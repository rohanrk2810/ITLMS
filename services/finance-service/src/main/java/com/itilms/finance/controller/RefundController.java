package com.itilms.finance.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.finance.dto.request.ReasonRequest;
import com.itilms.finance.dto.request.RefundRequest;
import com.itilms.finance.dto.response.RefundResponse;
import com.itilms.finance.entity.Refund;
import com.itilms.finance.service.RefundService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Refunds", description = "Money given back on a payment")
@RestController
@RequestMapping("/api/fees/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService service;
    private final com.itilms.finance.receipt.CreditNoteDocument creditNotes;

    @Operation(summary = "Ask for a refund", description = "ADMIN or FINANCE. The administrator then approves or rejects it.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse request(@Valid @RequestBody RefundRequest request) {
        return service.request(request);
    }

    @Operation(summary = "The credit note for a refund as a PDF",
            description = "Once the refund is approved. The finance desk, or the student the refund is for. Carries the tax of the original receipt in proportion.")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','COORDINATOR','STUDENT')")
    @GetMapping(value = "/{id}/credit-note", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    public org.springframework.http.ResponseEntity<byte[]> creditNote(@PathVariable Long id) {
        var note = creditNotes.pdf(id);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment().filename(note.fileName()).build().toString())
                .body(note.bytes());
    }

    @Operation(summary = "Refund requests", description = "Finance desk, administrator or coordinator.")
    @PreAuthorize(Roles.FINANCE_VIEW)
    @GetMapping
    public List<RefundResponse> list(@RequestParam(required = false) Refund.Status status) {
        return service.list(status);
    }

    @Operation(summary = "Approve a refund", description = "ADMIN only. The payment's refunded amount grows and the fee is owed again.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/{id}/approve")
    public RefundResponse approve(@PathVariable Long id, @RequestBody(required = false) ReasonRequest note) {
        return service.approve(id, note == null ? null : note.reason());
    }

    public record PayOutBody(@jakarta.validation.constraints.NotBlank String method,
                             @jakarta.validation.constraints.Size(max = 80) String reference,
                             java.time.LocalDate paidOn) {
    }

    @Operation(summary = "Record that an approved refund was paid out", description = "ADMIN or FINANCE. How, when and by whom the money was returned.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping("/{id}/paid")
    public RefundResponse paid(@PathVariable Long id, @Valid @RequestBody PayOutBody body) {
        return service.payOut(id, body.method(), body.reference(), body.paidOn());
    }

    @Operation(summary = "Reject a refund", description = "ADMIN only.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/{id}/reject")
    public RefundResponse reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest note) {
        return service.reject(id, note.reason());
    }
}
