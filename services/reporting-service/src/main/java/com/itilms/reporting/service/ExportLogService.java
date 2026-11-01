package com.itilms.reporting.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.reporting.dto.ExportLogRequest;
import com.itilms.reporting.entity.AuditLog;
import com.itilms.reporting.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * Notes in the audit log that someone downloaded a report. The file itself is built in the browser from data the
 * person could already see, so this cannot stop a download: it records who took what, and how much.
 */
@Service
@RequiredArgsConstructor
public class ExportLogService {

    private final AuditLogRepository repository;

    @Transactional
    public void record(ExportLogRequest request) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        Instant now = Instant.now();
        String detail = request.detail() == null ? "" : request.detail().trim();
        repository.save(AuditLog.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(now)
                .recordedAt(now)
                .serviceName("reporting-service")
                .actorUserId(caller.userId())
                .actorEmail(caller.email())
                .actorRole(caller.role())
                .action("REPORT_DOWNLOADED")
                .entityType(request.report().name())
                .entityId(detail.isEmpty() ? null : detail)
                .newValue("{\"rows\":" + request.rows() + "}")
                .build());
    }
}
