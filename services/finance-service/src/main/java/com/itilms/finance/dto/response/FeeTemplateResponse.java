package com.itilms.finance.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.itilms.finance.entity.CourseFeeTemplate;

public record FeeTemplateResponse(Long courseId, boolean configured, int installmentCount, int firstDueDays,
                                  List<Charge> charges) {

    public record Charge(String label, BigDecimal amount, int dueDays) {
    }

    public static FeeTemplateResponse from(CourseFeeTemplate t) {
        return new FeeTemplateResponse(t.getCourseId(), true, t.getInstallmentCount(), t.getFirstDueDays(),
                t.getCharges().stream().map(c -> new Charge(c.getLabel(), c.getAmount(), c.getDueDays())).toList());
    }

    /** What a course without a template gets: one installment due in a week, no extra heads. */
    public static FeeTemplateResponse defaults(Long courseId) {
        return new FeeTemplateResponse(courseId, false, 1, 7, List.of());
    }
}
