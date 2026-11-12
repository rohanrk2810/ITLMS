package com.itilms.finance.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.finance.service.CashClosingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@Tag(name = "Cash closing", description = "The day-end cash count")
@RestController
@RequestMapping("/api/fees/cash-closings")
@RequiredArgsConstructor
public class CashClosingController {

    private final CashClosingService service;

    public record CloseRequest(@NotNull LocalDate date,
                               @NotNull @DecimalMin("0.00") BigDecimal countedCash,
                               @Size(max = 255) String note) {
    }

    @Operation(summary = "The cash the system recorded on a day", description = "Defaults to today; also says whether the day is closed.")
    @PreAuthorize(Roles.FINANCE_VIEW)
    @GetMapping("/preview")
    public CashClosingService.Preview preview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.preview(date);
    }

    @Operation(summary = "Recent closings")
    @PreAuthorize(Roles.FINANCE_VIEW)
    @GetMapping
    public List<CashClosingService.Closing> recent() {
        return service.recent();
    }

    @Operation(summary = "Close a day", description = "ADMIN or FINANCE. A difference from the system needs a note.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CashClosingService.Closing close(@Valid @RequestBody CloseRequest request) {
        return service.close(request.date(), request.countedCash(), request.note());
    }
}
