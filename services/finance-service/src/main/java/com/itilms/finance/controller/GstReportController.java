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

    @Operation(summary = "GST collected on fees", description = "ADMIN or FINANCE. Month by month; empty while GST is off.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @GetMapping("/summary")
    public GstReportService.Report summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(from, to);
    }
}
