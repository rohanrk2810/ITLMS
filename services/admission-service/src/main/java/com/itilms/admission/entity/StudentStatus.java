package com.itilms.admission.entity;

/**
 * A student's standing with the institute.
 *
 * <p>Distinct from the account status in identity-service: an ALUMNI student
 * still signs in to download their certificate, so the account stays ACTIVE
 * while the academic record moves on.
 */
public enum StudentStatus {
    ACTIVE,
    ALUMNI,
    DROPPED,
    SUSPENDED
}
