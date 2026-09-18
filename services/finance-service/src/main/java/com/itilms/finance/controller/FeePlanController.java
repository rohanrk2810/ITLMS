package com.itilms.finance.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.Roles;
import com.itilms.finance.dto.request.FeePlanRequest;
import com.itilms.finance.dto.request.ReasonRequest;
import com.itilms.finance.dto.response.FeePlanResponse;
import com.itilms.finance.service.FeePlanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Fee plans (Doc S6.12). */
@Tag(name = "Fee plans", description = "Fees, discounts and installment schedules")
@RestController
@RequestMapping("/api/fee-plans")
@RequiredArgsConstructor
public class FeePlanController {

    private final FeePlanService feePlanService;

    @Operation(summary = "Raise a fee plan",
            description = "Usually raised automatically at admission; this is for plans agreed later. "
                    + "Give either installmentCount (even monthly split) or an explicit schedule that "
                    + "adds up to the net fee.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping
    public ResponseEntity<FeePlanResponse> create(@Valid @RequestBody FeePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feePlanService.create(request));
    }

    @Operation(summary = "List fee plans")
    @PreAuthorize(Roles.FINANCE_VIEW)
    @GetMapping
    public PageResponse<FeePlanResponse> list(@RequestParam(required = false) String status,
                                              @PageableDefault(size = 20) Pageable pageable) {
        return feePlanService.list(status, pageable);
    }

    @Operation(summary = "One fee plan", description = "With its schedule and payments.")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','COORDINATOR','STUDENT')")
    @GetMapping("/{id}")
    public FeePlanResponse get(@PathVariable Long id) {
        return feePlanService.get(id);
    }

    @Operation(summary = "Change a fee plan",
            description = "Fee, discount or schedule. The net fee cannot drop below what has been paid. "
                    + "The change is audited.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PutMapping("/{id}")
    public FeePlanResponse update(@PathVariable Long id, @Valid @RequestBody FeePlanRequest request) {
        return feePlanService.update(id, request);
    }

    @Operation(summary = "Cancel a fee plan", description = "Only when no money has been received on it.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping("/{id}/cancel")
    public FeePlanResponse cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return feePlanService.cancel(id, request.reason());
    }
}
