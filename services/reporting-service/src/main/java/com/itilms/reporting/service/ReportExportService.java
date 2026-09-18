package com.itilms.reporting.service;

import com.itilms.reporting.dto.AuditLogFilter;

public interface ReportExportService {

    ExportedFile exportAuditLogs(AuditLogFilter filter, ExportFormat format);

    record ExportedFile(byte[] content, String filename, String contentType) {
    }
}
