package com.itilms.reporting.service;

import org.springframework.data.domain.Pageable;

import com.itilms.common.dto.PageResponse;
import com.itilms.reporting.dto.AuditLogFilter;
import com.itilms.reporting.dto.AuditLogResponse;

public interface AuditLogService {

    PageResponse<AuditLogResponse> search(AuditLogFilter filter, Pageable pageable);

    AuditLogResponse get(Long id);
}
