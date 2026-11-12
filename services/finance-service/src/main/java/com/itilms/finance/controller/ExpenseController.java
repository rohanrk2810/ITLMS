package com.itilms.finance.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
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
import com.itilms.finance.entity.Expense;
import com.itilms.finance.service.ExpenseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@Tag(name = "Expenses", description = "What the institute spends, and profit or loss")
@RestController
@RequestMapping("/api/fees/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService service;

    public record ExpenseBody(LocalDate date,
                              @NotNull Expense.Category category,
                              @NotNull @DecimalMin("0.01") BigDecimal amount,
                              @NotBlank @Size(max = 255) String description,
                              @Size(max = 120) String paidTo,
                              @Size(max = 80) String referenceNo) {
    }

    @Operation(summary = "Record an expense", description = "ADMIN or FINANCE.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseService.ExpenseView add(@Valid @RequestBody ExpenseBody body) {
        return service.add(new ExpenseService.ExpenseRequest(body.date(), body.category(), body.amount(),
                body.description(), body.paidTo(), body.referenceNo()));
    }

    @Operation(summary = "Expenses in a date range", description = "ADMIN or FINANCE.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @GetMapping
    public List<ExpenseService.ExpenseView> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(from, to);
    }

    @Operation(summary = "Profit and loss", description = "Fees collected (net of refunds) less expenses, with expenses by category.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @GetMapping("/summary")
    public ExpenseService.Summary summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(from, to);
    }

    @Operation(summary = "Void an expense", description = "ADMIN or FINANCE. It stays on record, marked void, and no longer counts.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping("/{id}/void")
    public ExpenseService.ExpenseView voidExpense(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return service.voidExpense(id, request.reason());
    }
}
