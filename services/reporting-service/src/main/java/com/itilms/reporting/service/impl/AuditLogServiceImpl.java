package com.itilms.reporting.service.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.reporting.dto.AuditLogFilter;
import com.itilms.reporting.dto.AuditLogResponse;
import com.itilms.reporting.repository.AuditLogRepository;
import com.itilms.reporting.service.AuditLogService;
import com.itilms.reporting.specification.AuditLogSpecifications;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository repository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(AuditLogFilter filter, Pageable pageable) {
        return PageResponse.from(repository.findAll(AuditLogSpecifications.build(filter), pageable),
                AuditLogResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogResponse get(Long id) {
        return repository.findById(id).map(AuditLogResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Audit log entry", id));
    }
}
