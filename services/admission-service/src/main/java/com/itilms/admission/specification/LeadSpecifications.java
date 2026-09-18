package com.itilms.admission.specification;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

import com.itilms.admission.entity.Lead;
import com.itilms.admission.entity.LeadSource;
import com.itilms.admission.entity.LeadStatus;
import com.itilms.common.exception.BusinessRuleException;

public final class LeadSpecifications {

    private LeadSpecifications() {
    }

    public static Specification<Lead> hasStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        LeadStatus parsed;
        try {
            parsed = LeadStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unknown lead status: " + status);
        }
        return (root, query, cb) -> cb.equal(root.get("status"), parsed);
    }

    public static Specification<Lead> hasSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        LeadSource parsed;
        try {
            parsed = LeadSource.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unknown lead source: " + source);
        }
        return (root, query, cb) -> cb.equal(root.get("source"), parsed);
    }

    public static Specification<Lead> assignedTo(Long counselorUserId) {
        if (counselorUserId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("counselorUserId"), counselorUserId);
    }

    /** Only leads still worth working — the counselor's default view. */
    public static Specification<Lead> openOnly(boolean openOnly) {
        if (!openOnly) {
            return null;
        }
        return (root, query, cb) -> root.get("status").in(
                LeadStatus.NEW, LeadStatus.CONTACTED, LeadStatus.FOLLOW_UP, LeadStatus.INTERESTED);
    }

    public static Specification<Lead> followUpDueBefore(Instant cutoff) {
        if (cutoff == null) {
            return null;
        }
        return (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("nextFollowUpAt")),
                cb.lessThan(root.get("nextFollowUpAt"), cutoff));
    }

    public static Specification<Lead> matches(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(root.get("phone"), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("email"), "")), pattern));
    }
}
