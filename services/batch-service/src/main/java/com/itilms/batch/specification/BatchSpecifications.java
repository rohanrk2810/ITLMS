package com.itilms.batch.specification;

import org.springframework.data.jpa.domain.Specification;

import com.itilms.batch.entity.Batch;
import com.itilms.batch.entity.BatchMode;
import com.itilms.batch.entity.BatchStatus;
import com.itilms.common.exception.BusinessRuleException;

public final class BatchSpecifications {

    private BatchSpecifications() {
    }

    public static Specification<Batch> hasStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        BatchStatus parsed;
        try {
            parsed = BatchStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Status must be PLANNED, ONGOING, COMPLETED or CANCELLED");
        }
        return (root, query, cb) -> cb.equal(root.get("status"), parsed);
    }

    public static Specification<Batch> hasMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        BatchMode parsed;
        try {
            parsed = BatchMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Mode must be ONLINE, OFFLINE or HYBRID");
        }
        return (root, query, cb) -> cb.equal(root.get("mode"), parsed);
    }

    public static Specification<Batch> forCourse(Long courseId) {
        return courseId == null ? null : (root, query, cb) -> cb.equal(root.get("courseId"), courseId);
    }

    public static Specification<Batch> forTrainer(Long trainerId) {
        return trainerId == null ? null : (root, query, cb) -> cb.equal(root.get("trainerId"), trainerId);
    }

    public static Specification<Batch> matches(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("batchCode")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("name"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("courseTitle"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("trainerName"), "")), pattern));
    }
}
