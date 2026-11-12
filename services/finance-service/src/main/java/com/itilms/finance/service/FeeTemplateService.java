package com.itilms.finance.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.finance.dto.request.FeeTemplateRequest;
import com.itilms.finance.dto.response.FeeTemplateResponse;
import com.itilms.finance.entity.CourseFeeTemplate;
import com.itilms.finance.repository.CourseFeeTemplateRepository;

import lombok.RequiredArgsConstructor;

/** Reads and saves the usual fee structure of a course. */
@Service
@RequiredArgsConstructor
public class FeeTemplateService {

    private final CourseFeeTemplateRepository repository;

    @Transactional(readOnly = true)
    public FeeTemplateResponse get(Long courseId) {
        return repository.findById(courseId).map(FeeTemplateResponse::from).orElse(FeeTemplateResponse.defaults(courseId));
    }

    @Transactional
    public FeeTemplateResponse save(Long courseId, FeeTemplateRequest request) {
        CourseFeeTemplate template = repository.findById(courseId).orElseGet(() -> new CourseFeeTemplate(courseId));
        template.setInstallmentCount(request.installmentCount());
        template.setFirstDueDays(request.firstDueDays());
        template.getCharges().clear();
        if (request.charges() != null) {
            request.charges().forEach(c -> template.getCharges().add(
                    new CourseFeeTemplate.Charge(c.label().trim(), c.amount().setScale(2, java.math.RoundingMode.HALF_UP),
                            c.dueDays())));
        }
        return FeeTemplateResponse.from(repository.save(template));
    }
}
