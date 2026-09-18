package com.itilms.reporting.controller;

import java.time.Instant;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.reporting.dto.AuditLogFilter;
import com.itilms.reporting.service.ExportFormat;
import com.itilms.reporting.service.ReportExportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** CSV, Excel and PDF exports (Doc S15), capped at itilms.reporting.max-export-rows. */
@Tag(name = "Reports", description = "CSV, Excel and PDF exports of the audit trail")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportExportService exportService;

    @Operation(summary = "Export the audit trail", description = "Same filters as GET /api/audit-logs.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @GetMapping("/audit-logs/export")
    public ResponseEntity<byte[]> exportAuditLogs(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "CSV") ExportFormat format) {
        var filter = new AuditLogFilter(serviceName, action, entityType, actorUserId, from, to);
        var file = exportService.exportAuditLogs(filter, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
