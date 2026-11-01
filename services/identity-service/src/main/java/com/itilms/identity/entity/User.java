package com.itilms.identity.entity;

import java.time.Instant;

import com.itilms.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An account that can sign in.
 *
 * <p>The password field is named {@code passwordHash} rather than
 * {@code password} on purpose: the name makes it obvious at every call site
 * that the plain text never lives here, and makes a mistaken assignment of a
 * raw password stand out in review.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(length = 20)
    private String phone;

    /** BCrypt digest. Never logged, never serialised into any DTO. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * Student or trainer id, copied from admission-service's
     * {@code ProfileLinkedEvent}. Written only by the Kafka consumer.
     */
    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "profile_code", length = 30)
    private String profileCode;

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** A personal sidebar setting (comma-separated hidden app paths); null follows the institute's student menu. */
    @Column(name = "hidden_menu")
    private String hiddenMenu;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Extra duties an administrator gave this person, comma-separated (COORDINATOR, FINANCE, PLACEMENT); null for none. */
    @Column(name = "extra_roles", length = 100)
    private String extraRoles;

    /** Goes up every time the duties change; it travels in the access token so the gateway can tell a token is stale. */
    @Column(name = "roles_version", nullable = false)
    @Builder.Default
    private int rolesVersion = 0;

    public java.util.List<String> extraRoleList() {
        if (extraRoles == null || extraRoles.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(extraRoles.split(",")).map(String::trim).filter(r -> !r.isEmpty()).toList();
    }

    /** Replaces the duties and moves the version on, so every token issued before is now out of date. */
    public void changeExtraRoles(java.util.List<String> roles) {
        this.extraRoles = roles.isEmpty() ? null : String.join(",", roles);
        this.rolesVersion += 1;
    }

    /**
     * Wipes what identifies the person and marks the account deleted. The e-mail is replaced by one that is unique
     * per id (the column is mandatory and unique) and can never be mailed; the phone is cleared so the number is free
     * for a new account.
     */
    public void markDeleted(String unusablePasswordHash) {
        this.firstName = "Deleted";
        this.lastName = "user";
        this.email = "deleted-" + id + "@deleted.invalid";
        this.phone = null;
        this.passwordHash = unusablePasswordHash;
        this.status = UserStatus.DELETED;
        this.mustChangePassword = false;
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.deletedAt = Instant.now();
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    /** True while a brute-force lockout is still in effect. */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public void recordSuccessfulLogin() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
    }

    /**
     * Counts a failed attempt and locks the account once the limit is reached.
     *
     * @return true if this attempt triggered a lockout
     */
    public boolean recordFailedLogin(int maxAttempts, java.time.Duration lockoutDuration) {
        this.failedAttempts++;
        if (this.failedAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plus(lockoutDuration);
            return true;
        }
        return false;
    }
}
