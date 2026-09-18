package com.itilms.placement.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.placement.dto.request.CompanyRequest;
import com.itilms.placement.dto.response.CompanyResponse;
import com.itilms.placement.entity.Company;
import com.itilms.placement.repository.CompanyRepository;

import lombok.RequiredArgsConstructor;

/** Recruiters (Doc S6.14). */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final EventPublisher events;

    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        if (companyRepository.nameTaken(request.name(), null)) {
            throw new DuplicateResourceException("A company called \"%s\" already exists.".formatted(request.name().trim()));
        }
        Company company = new Company();
        apply(company, request);
        company = companyRepository.save(company);
        events.audit("placement-service", "COMPANY_CREATED", "Company", company.getId(), null,
                Map.of("name", company.getName()));
        return CompanyResponse.from(company);
    }

    @Transactional
    public CompanyResponse update(Long id, CompanyRequest request) {
        Company company = require(id);
        if (companyRepository.nameTaken(request.name(), id)) {
            throw new DuplicateResourceException("A company called \"%s\" already exists.".formatted(request.name().trim()));
        }
        apply(company, request);
        return CompanyResponse.from(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> list(boolean activeOnly) {
        return (activeOnly ? companyRepository.findByActiveTrueOrderByNameAsc() : companyRepository.findAllByOrderByNameAsc())
                .stream().map(CompanyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(Long id) {
        return CompanyResponse.from(require(id));
    }

    Company require(Long id) {
        return companyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Company", id));
    }

    private static void apply(Company company, CompanyRequest request) {
        company.setName(request.name().trim());
        company.setIndustry(trim(request.industry()));
        company.setWebsite(trim(request.website()));
        company.setLocation(trim(request.location()));
        company.setContactName(trim(request.contactName()));
        company.setContactEmail(trim(request.contactEmail()));
        company.setContactPhone(trim(request.contactPhone()));
        company.setNotes(trim(request.notes()));
        company.setActive(request.active() == null || request.active());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
