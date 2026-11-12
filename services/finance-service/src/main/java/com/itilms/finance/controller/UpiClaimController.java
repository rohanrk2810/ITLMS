package com.itilms.finance.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.finance.dto.request.ReasonRequest;
import com.itilms.finance.service.UpiClaimService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@Tag(name = "UPI payments", description = "Pay the institute's UPI id, then report the transaction reference")
@RestController
@RequestMapping("/api/fees/upi")
@RequiredArgsConstructor
public class UpiClaimController {

    private final UpiClaimService service;

    public record ClaimBody(@NotNull Long feePlanId, @NotNull @DecimalMin("1.00") BigDecimal amount,
                            @NotBlank @Size(max = 40) String utr) {
    }

    @Operation(summary = "The institute's UPI id", description = "Signed-in students. Disabled until the institute sets one.")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/config")
    public UpiClaimService.Config config() {
        return service.config();
    }

    @Operation(summary = "Report a UPI payment", description = "A student, for their own fee plan, with the transaction reference.")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/claims")
    @ResponseStatus(HttpStatus.CREATED)
    public UpiClaimService.ClaimView claim(@Valid @RequestBody ClaimBody body) {
        return service.claim(new UpiClaimService.ClaimRequest(body.feePlanId(), body.amount(), body.utr()));
    }

    @Operation(summary = "My UPI claims")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/claims/mine")
    public List<UpiClaimService.ClaimView> mine() {
        return service.mine();
    }

    @Operation(summary = "All UPI claims", description = "Finance desk, administrator or coordinator.")
    @PreAuthorize(Roles.FINANCE_VIEW)
    @GetMapping("/claims")
    public List<UpiClaimService.ClaimView> all() {
        return service.all();
    }

    @Operation(summary = "Confirm a claim", description = "ADMIN or FINANCE, after matching the reference with the bank. Records the payment.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping("/claims/{id}/confirm")
    public UpiClaimService.ClaimView confirm(@PathVariable Long id) {
        return service.confirm(id);
    }

    @Operation(summary = "Reject a claim", description = "ADMIN or FINANCE. The student is told.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping("/claims/{id}/reject")
    public UpiClaimService.ClaimView reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return service.reject(id, request.reason());
    }
}
