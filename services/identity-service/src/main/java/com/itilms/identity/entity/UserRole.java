package com.itilms.identity.entity;

import java.util.Arrays;

import com.itilms.common.exception.BusinessRuleException;

/** The six roles from Doc Section 4. Stored as text, matching the CHECK constraint. */
public enum UserRole {

    ADMIN("Super Admin / Admin", "Overall institute administration"),
    COORDINATOR("Academic Coordinator", "Academic operations"),
    TRAINER("Trainer", "Teaching and assessment"),
    STUDENT("Student", "Learning and self-service"),
    PLACEMENT("Placement / Counselor", "Admissions and placements"),
    FINANCE("Finance / Accounts", "Fee operations");

    private final String displayName;
    private final String responsibility;

    UserRole(String displayName, String responsibility) {
        this.displayName = displayName;
        this.responsibility = responsibility;
    }

    public String displayName() {
        return displayName;
    }

    public String responsibility() {
        return responsibility;
    }

    /** A role that comes with a domain profile in admission-service. */
    public boolean hasProfile() {
        return this == STUDENT || this == TRAINER;
    }

    public static UserRole of(String value) {
        return Arrays.stream(values())
                .filter(role -> role.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "Unknown role '%s'. Valid roles: %s".formatted(value, Arrays.toString(values()))));
    }
}
