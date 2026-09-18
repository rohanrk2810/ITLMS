package com.itilms.finance.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.finance.dto.response.FeePlanResponse;
import com.itilms.finance.dto.response.FinanceDashboardResponse;
import com.itilms.finance.dto.response.OverdueItemResponse;
import com.itilms.finance.service.FeePlanService;
import com.itilms.finance.service.FinanceReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Fee summaries (Doc S6.12, S11, S15). */
@Tag(name = "Fees", description = "What students owe, and the finance dashboard")
@RestController
@RequestMapping("/api/fees")
@RequiredArgsConstructor
public class FeeController {

    private final FeePlanService feePlanService;
    private final FinanceReportService reportService;

    @Operation(summary = "My fees",
            description = "Each fee plan with its schedule, what is paid, what is outstanding (net fee "
                    + "minus successful payments, Doc S14) and every payment made.")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me")
    public List<FeePlanResponse> mine() {
        return feePlanService.mine();
    }

    @Operation(summary = "A student's fees", description = "Finance and coordinators; a student only their own.")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','COORDINATOR','STUDENT')")
    @GetMapping("/students/{studentId}")
    public List<FeePlanResponse> forStudent(@PathVariable Long studentId) {
        return feePlanService.forStudent(studentId);
    }

    @Operation(summary = "Finance dashboard",
            description = "Billed, collected, pending, overdue, and this month's collections by method (Doc S15).")
    @PreAuthorize(Roles.FINANCE_VIEW)
    @GetMapping("/dashboard")
    public FinanceDashboardResponse dashboard() {
        return reportService.dashboard();
    }

    @Operation(summary = "Overdue installments",
            description = "Every installment past its due date and not fully paid, longest overdue first.")
    @PreAuthorize(Roles.FINANCE_VIEW)
    @GetMapping("/overdue")
    public List<OverdueItemResponse> overdue() {
        return reportService.overdue();
    }
}
