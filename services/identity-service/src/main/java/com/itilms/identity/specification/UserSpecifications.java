package com.itilms.identity.specification;

import org.springframework.data.jpa.domain.Specification;

import com.itilms.identity.entity.User;
import com.itilms.identity.entity.UserRole;
import com.itilms.identity.entity.UserStatus;

import jakarta.persistence.criteria.Predicate;

/**
 * Composable filters for the user list.
 *
 * <p>Doc S8.5 asks for tables with search, filter and sort. Building the query
 * from specifications keeps every filter optional and independent, so the
 * controller does not fan out into a method per combination — and, more
 * importantly, keeps user input inside bound parameters rather than
 * concatenated SQL.
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> hasRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        UserRole parsed = UserRole.of(role);
        return (root, query, cb) -> cb.equal(root.get("role"), parsed);
    }

    public static Specification<User> hasStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        UserStatus parsed = UserStatus.valueOf(status.toUpperCase());
        return (root, query, cb) -> cb.equal(root.get("status"), parsed);
    }

    /**
     * Matches a name fragment, an email or a phone number with one box.
     *
     * <p>Staff searching for a student type whatever they have to hand — half a
     * surname, the phone number on a form. Making them choose a field first is
     * friction for no benefit.
     */
    public static Specification<User> matches(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            Predicate firstName = cb.like(cb.lower(root.get("firstName")), pattern);
            Predicate lastName = cb.like(cb.lower(root.get("lastName")), pattern);
            Predicate email = cb.like(cb.lower(root.get("email")), pattern);
            Predicate phone = cb.like(cb.coalesce(root.get("phone"), ""), pattern);
            Predicate profileCode = cb.like(cb.lower(cb.coalesce(root.get("profileCode"), "")), pattern);
            return cb.or(firstName, lastName, email, phone, profileCode);
        };
    }

    /** Excludes deactivated accounts from pickers and dropdowns. */
    public static Specification<User> activeOnly() {
        return (root, query, cb) -> cb.equal(root.get("status"), UserStatus.ACTIVE);
    }
}
