package com.itilms.reporting.controller;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.Roles;
import com.itilms.reporting.dto.AuditLogFilter;
import com.itilms.reporting.dto.AuditLogResponse;
import com.itilms.reporting.service.AuditLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** The institute-wide audit trail (Doc S5, S12, S14). Admin only: it names other people's actions. */
@Tag(name = "Audit log", description = "One chronological trail across all twelve services")
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService service;

    @Operation(summary = "Search the audit trail", description = "Every filter is optional and they combine.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @GetMapping
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 30) Pageable pageable) {
        return service.search(new AuditLogFilter(serviceName, action, entityType, actorUserId, from, to), pageable);
    }

    @Operation(summary = "One audit entry")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @GetMapping("/{id}")
    public AuditLogResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
