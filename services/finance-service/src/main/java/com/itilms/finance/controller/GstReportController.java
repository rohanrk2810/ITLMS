package com.itilms.finance.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.finance.service.GstReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "GST", description = "Tax collected on fees")
@RestController
@RequestMapping("/api/fees/gst")
@RequiredArgsConstructor
public class GstReportController {

    private final GstReportService service;
    private final com.itilms.finance.service.GstSettingsService settings;

    public record SettingsBody(@jakarta.validation.constraints.NotNull java.math.BigDecimal percent, String gstin, String sacCode,
                               LocalDate effectiveFrom) {
    }

    @Operation(summary = "The GST settings", description = "ADMIN or FINANCE.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @GetMapping("/settings")
    public com.itilms.finance.service.GstSettingsService.Settings settings() {
        return settings.current();
    }

    @Operation(summary = "Set the GST rate", description = "ADMIN only. The rate applies from the effective date (today if empty); "
            + "earlier payments, receipts and books are left as they were. A rate of 0 switches GST off.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @org.springframework.web.bind.annotation.PutMapping("/settings")
    public com.itilms.finance.service.GstSettingsService.Settings saveSettings(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody SettingsBody body) {
        return settings.save(new com.itilms.finance.service.GstSettingsService.Update(body.percent(), body.gstin(),
                body.sacCode(), body.effectiveFrom()));
    }

    @Operation(summary = "GST collected on fees", description = "ADMIN or FINANCE. Month by month; empty while GST is off.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @GetMapping("/summary")
    public GstReportService.Report summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(from, to);
    }
}
