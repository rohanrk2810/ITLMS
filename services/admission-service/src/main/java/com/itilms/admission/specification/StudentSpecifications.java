package com.itilms.admission.specification;

import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;

import com.itilms.admission.entity.Student;
import com.itilms.admission.entity.StudentStatus;
import com.itilms.common.exception.BusinessRuleException;

/** Optional, composable filters for the student list. */
public final class StudentSpecifications {

    private StudentSpecifications() {
    }

    public static Specification<Student> hasStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        StudentStatus parsed;
        try {
            parsed = StudentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Status must be ACTIVE, ALUMNI, DROPPED or SUSPENDED");
        }
        return (root, query, cb) -> cb.equal(root.get("status"), parsed);
    }

    /** One search box across name, code, email and phone. */
    public static Specification<Student> matches(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern),
                cb.like(cb.lower(root.get("studentCode")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("email"), "")), pattern),
                cb.like(cb.coalesce(root.get("phone"), ""), pattern));
    }

    public static Specification<Student> admittedBetween(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("admissionDate"), from, to);
            }
            return from != null
                    ? cb.greaterThanOrEqualTo(root.get("admissionDate"), from)
                    : cb.lessThanOrEqualTo(root.get("admissionDate"), to);
        };
    }
}
