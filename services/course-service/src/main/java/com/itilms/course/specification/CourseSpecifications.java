package com.itilms.course.specification;

import org.springframework.data.jpa.domain.Specification;

import com.itilms.common.exception.BusinessRuleException;
import com.itilms.course.entity.Course;
import com.itilms.course.entity.CourseLevel;
import com.itilms.course.entity.CourseStatus;

public final class CourseSpecifications {

    private CourseSpecifications() {
    }

    public static Specification<Course> hasStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        CourseStatus parsed;
        try {
            parsed = CourseStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Status must be DRAFT, PUBLISHED or ARCHIVED");
        }
        return (root, query, cb) -> cb.equal(root.get("status"), parsed);
    }

    /**
     * Pins the status to PUBLISHED.
     *
     * <p>Used by the public catalog instead of {@link #hasStatus}, so the filter
     * comes from the code path rather than from a query parameter an anonymous
     * caller controls.
     */
    public static Specification<Course> published() {
        return (root, query, cb) -> cb.equal(root.get("status"), CourseStatus.PUBLISHED);
    }

    public static Specification<Course> hasLevel(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }
        CourseLevel parsed;
        try {
            parsed = CourseLevel.valueOf(level.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Level must be BEGINNER, INTERMEDIATE or ADVANCED");
        }
        return (root, query, cb) -> cb.equal(root.get("level"), parsed);
    }

    public static Specification<Course> matches(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("technologyStack"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("summary"), "")), pattern));
    }
}
