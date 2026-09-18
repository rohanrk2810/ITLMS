package com.itilms.reporting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.itilms.reporting.config.ReportingProperties;
import com.itilms.reporting.dto.AuditLogFilter;
import com.itilms.reporting.entity.AuditLog;
import com.itilms.reporting.repository.AuditLogRepository;
import com.itilms.reporting.service.ExportFormat;
import com.itilms.reporting.service.ReportExportService;

/** Doc S15: an export is capped at max-export-rows, in the format the caller asked for. */
@ExtendWith(MockitoExtension.class)
class ReportExportServiceImplTest {

    @Mock private AuditLogRepository repository;

    private ReportExportServiceImpl service;

    private static final AuditLogFilter NO_FILTER = new AuditLogFilter(null, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        ReportingProperties props = new ReportingProperties();
        props.setMaxExportRows(2);
        service = new ReportExportServiceImpl(repository, props);

        AuditLog entry = AuditLog.builder().id(1L).occurredAt(Instant.parse("2026-09-18T10:00:00Z"))
                .serviceName("identity-service").actorEmail("admin@x").actorRole("ADMIN")
                .action("PASSWORD_RESET").entityType("User").entityId("7").build();
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<AuditLog>>any(),
                org.mockito.ArgumentMatchers.<PageRequest>any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(entry)));
    }

    @Test
    @DisplayName("CSV export carries the row and names the file .csv")
    void csvExportContainsTheRow() {
        ReportExportService.ExportedFile file = service.exportAuditLogs(NO_FILTER, ExportFormat.CSV);

        assertThat(file.filename()).endsWith(".csv");
        assertThat(file.contentType()).isEqualTo("text/csv");
        assertThat(new String(file.content(), StandardCharsets.UTF_8)).contains("PASSWORD_RESET");
    }

    @Test
    @DisplayName("Excel export is named .xlsx with the spreadsheet content type")
    void excelExportUsesTheRightContentType() {
        ReportExportService.ExportedFile file = service.exportAuditLogs(NO_FILTER, ExportFormat.XLSX);

        assertThat(file.filename()).endsWith(".xlsx");
        assertThat(file.contentType()).contains("spreadsheetml");
    }

    @Test
    @DisplayName("PDF export is named .pdf and is a real PDF")
    void pdfExportIsAPdfDocument() {
        ReportExportService.ExportedFile file = service.exportAuditLogs(NO_FILTER, ExportFormat.PDF);

        assertThat(file.filename()).endsWith(".pdf");
        assertThat(new String(file.content(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("The page size passed to the repository never exceeds max-export-rows")
    void exportIsCappedAtMaxExportRows() {
        service.exportAuditLogs(NO_FILTER, ExportFormat.CSV);

        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(repository).findAll(org.mockito.ArgumentMatchers.<Specification<AuditLog>>any(), pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(2);
    }
}
