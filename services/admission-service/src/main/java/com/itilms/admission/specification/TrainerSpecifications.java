package com.itilms.admission.specification;

import org.springframework.data.jpa.domain.Specification;

import com.itilms.admission.entity.Trainer;
import com.itilms.admission.entity.TrainerStatus;
import com.itilms.common.exception.BusinessRuleException;

public final class TrainerSpecifications {

    private TrainerSpecifications() {
    }

    public static Specification<Trainer> hasStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        TrainerStatus parsed;
        try {
            parsed = TrainerStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Status must be ACTIVE, INACTIVE or ON_LEAVE");
        }
        return (root, query, cb) -> cb.equal(root.get("status"), parsed);
    }

    public static Specification<Trainer> matches(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern),
                cb.like(cb.lower(root.get("employeeCode")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("specialization"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("email"), "")), pattern));
    }
}
