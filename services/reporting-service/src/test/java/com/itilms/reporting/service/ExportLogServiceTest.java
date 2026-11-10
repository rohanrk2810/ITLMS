package com.itilms.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.common.security.AppPrincipal;
import com.itilms.reporting.dto.ExportLogRequest;
import com.itilms.reporting.entity.AuditLog;
import com.itilms.reporting.repository.AuditLogRepository;

@ExtendWith(MockitoExtension.class)
class ExportLogServiceTest {

    @Mock AuditLogRepository repository;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(String role) {
        AppPrincipal p = new AppPrincipal(7L, "anita@example.com", "Anita", role, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.authorities()));
    }

    @Test
    void aDownloadIsRecordedWithWhoTookItWhichReportAndHowManyRows() {
        signInAs("COORDINATOR");
        ExportLogService service = new ExportLogService(repository);
        when(repository.save(org.mockito.ArgumentMatchers.any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        service.record(new ExportLogRequest(ExportedReport.LEADS, 120, "  open leads  "));

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(saved.capture());
        AuditLog row = saved.getValue();
        assertThat(row.getAction()).isEqualTo("REPORT_DOWNLOADED");
        assertThat(row.getEntityType()).isEqualTo("LEADS");
        assertThat(row.getEntityId()).isEqualTo("open leads");
        assertThat(row.getActorEmail()).isEqualTo("anita@example.com");
        assertThat(row.getActorRole()).isEqualTo("COORDINATOR");
        assertThat(row.getNewValue()).isEqualTo("{\"rows\":120}");
        assertThat(row.getEventId()).isNotBlank();
        assertThat(row.getRecordedAt()).isNotNull();
    }

    @Test
    void everyReportTheBrowserCanBuildIsKnownByName() {
        assertThat(ExportedReport.values()).extracting(Enum::name).contains(
                "LEADS", "ATTENDANCE", "RESULTS", "DASHBOARD", "PROGRESS", "WATCH_REPORT", "FEE_POSITION", "FEE_PLANS", "OVERDUE_FEES", "USERS", "COURSE_REQUESTS");
    }

    @Test
    void aBlankDetailIsLeftOut() {
        signInAs("ADMIN");
        ExportLogService service = new ExportLogService(repository);
        when(repository.save(org.mockito.ArgumentMatchers.any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        service.record(new ExportLogRequest(ExportedReport.DASHBOARD, 21, "   "));

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEntityId()).isNull();
    }

    @Test
    void nobodyIsRecordedWhenNobodyIsSignedIn() {
        ExportLogService service = new ExportLogService(repository);

        assertThatThrownBy(() -> service.record(new ExportLogRequest(ExportedReport.RESULTS, 1, null)))
                .isInstanceOf(RuntimeException.class);
    }
}
