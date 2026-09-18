package com.itilms.common.security;

/**
 * Canonical role names. These are the six roles defined in the project
 * documentation, Section 4 (User Roles &amp; Permissions).
 *
 * <p>Spring Security expects authorities to carry the {@code ROLE_} prefix when
 * used through {@code hasRole(...)}, so both spellings are exposed: the bare
 * name (what the JWT carries and what the database stores) and the prefixed
 * authority (what {@code @PreAuthorize} matches on).
 */
public final class Roles {

    private Roles() {
    }

    public static final String ADMIN = "ADMIN";
    public static final String COORDINATOR = "COORDINATOR";
    public static final String TRAINER = "TRAINER";
    public static final String STUDENT = "STUDENT";
    public static final String PLACEMENT = "PLACEMENT";
    public static final String FINANCE = "FINANCE";

    public static final String AUTHORITY_PREFIX = "ROLE_";

    /** Institute-level master data managers (Doc S14: "only authorized staff"). */
    public static final String STAFF = "hasAnyRole('ADMIN','COORDINATOR')";
    /** Anyone who may look at academic delivery data. */
    public static final String ACADEMIC = "hasAnyRole('ADMIN','COORDINATOR','TRAINER')";
    public static final String ADMIN_ONLY = "hasRole('ADMIN')";
    public static final String FINANCE_DESK = "hasAnyRole('ADMIN','FINANCE')";
    /** May look at fee figures without changing them - coordinators chasing a student's dues. */
    public static final String FINANCE_VIEW = "hasAnyRole('ADMIN','FINANCE','COORDINATOR')";
    public static final String PLACEMENT_DESK = "hasAnyRole('ADMIN','PLACEMENT')";

    public static String authority(String role) {
        return AUTHORITY_PREFIX + role;
    }
}
