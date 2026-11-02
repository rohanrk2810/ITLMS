package com.itilms.reporting.service;

import com.itilms.reporting.dto.AuditLogFilter;

public interface ReportExportService {

    /** Every column. */
    default ExportedFile exportAuditLogs(AuditLogFilter filter, ExportFormat format) {
        return exportAuditLogs(filter, format, null);
    }

    /** Only the named columns (by their header, in the report's own order); null or empty means every column. */
    ExportedFile exportAuditLogs(AuditLogFilter filter, ExportFormat format, java.util.List<String> columns);

    record ExportedFile(byte[] content, String filename, String contentType) {
    }
}
