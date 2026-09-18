package com.itilms.reporting.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.reporting.config.ReportingProperties;
import com.itilms.reporting.dto.AuditLogFilter;
import com.itilms.reporting.entity.AuditLog;
import com.itilms.reporting.repository.AuditLogRepository;
import com.itilms.reporting.service.ExportFormat;
import com.itilms.reporting.service.ReportExportService;
import com.itilms.reporting.service.export.TabularExporter;
import com.itilms.reporting.specification.AuditLogSpecifications;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportExportServiceImpl implements ReportExportService {

    private static final List<String> HEADERS =
            List.of("Time", "Service", "Actor", "Role", "Action", "Entity type", "Entity id");

    private final AuditLogRepository repository;
    private final ReportingProperties props;

    @Override
    @Transactional(readOnly = true)
    public ExportedFile exportAuditLogs(AuditLogFilter filter, ExportFormat format) {
        Specification<AuditLog> spec = AuditLogSpecifications.build(filter);
        // Doc S15: an unbounded export is a denial of service dressed as a feature request.
        PageRequest capped = PageRequest.of(0, props.getMaxExportRows(), Sort.by(Sort.Direction.DESC, "occurredAt"));
        List<List<String>> rows = repository.findAll(spec, capped).getContent().stream().map(this::toRow).toList();

        String base = "audit-log-" + LocalDate.now();
        return switch (format) {
            case CSV -> new ExportedFile(TabularExporter.csv(HEADERS, rows), base + ".csv", "text/csv");
            case XLSX -> new ExportedFile(TabularExporter.excel("Audit log", HEADERS, rows), base + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case PDF -> new ExportedFile(TabularExporter.pdf("Audit log", HEADERS, rows), base + ".pdf",
                    "application/pdf");
        };
    }

    private List<String> toRow(AuditLog log) {
        return List.of(
                String.valueOf(log.getOccurredAt()),
                log.getServiceName(),
                log.getActorEmail() == null ? "system" : log.getActorEmail(),
                log.getActorRole() == null ? "" : log.getActorRole(),
                log.getAction(),
                log.getEntityType() == null ? "" : log.getEntityType(),
                log.getEntityId() == null ? "" : log.getEntityId());
    }
}
