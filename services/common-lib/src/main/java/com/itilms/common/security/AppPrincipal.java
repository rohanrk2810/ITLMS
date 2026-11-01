package com.itilms.common.security;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated caller, reconstructed from the JWT on every request.
 *
 * <p>No downstream service performs a database lookup to know who is calling:
 * everything needed for an authorization decision travels inside the token.
 *
 * <p>A person has one main role and, when an administrator has given them extra duties, up to a few more (a trainer
 * who also does the coordinator's work). Every one of them is an authority, so {@code hasAnyRole(...)} on an endpoint
 * lets the person in through any of them. The student or trainer profile id belongs to the main role.
 *
 * @param userId     {@code users.id} in identity-service
 * @param email      login email, useful for audit trails
 * @param fullName   display name, so services can render "marked by ..." without a lookup
 * @param role       the main role, one of {@link Roles}
 * @param profileId  student id for STUDENT, trainer id for TRAINER, {@code null} otherwise
 * @param extraRoles extra duties given by an administrator; never contains the main role, ADMIN, STUDENT or TRAINER
 */
public record AppPrincipal(
        Long userId,
        String email,
        String fullName,
        String role,
        Long profileId,
        List<String> extraRoles
) implements Serializable {

    public AppPrincipal {
        extraRoles = extraRoles == null ? List.of() : List.copyOf(extraRoles);
    }

    /** A caller with no extra duties. */
    public AppPrincipal(Long userId, String email, String fullName, String role, Long profileId) {
        this(userId, email, fullName, role, profileId, List.of());
    }

    public Collection<? extends GrantedAuthority> authorities() {
        List<GrantedAuthority> all = new ArrayList<>();
        all.add(new SimpleGrantedAuthority(Roles.authority(role)));
        for (String extra : extraRoles) {
            all.add(new SimpleGrantedAuthority(Roles.authority(extra)));
        }
        return all;
    }

    /** Whether the person holds this role, as their main role or as an extra duty. */
    public boolean hasRole(String wanted) {
        return wanted != null && (wanted.equals(role) || extraRoles.contains(wanted));
    }

    public boolean isStudent() {
        return hasRole(Roles.STUDENT);
    }

    /** A trainer by main role: only then is {@link #profileId()} a trainer id. */
    public boolean isTrainer() {
        return Roles.TRAINER.equals(role);
    }

    public boolean isAdmin() {
        return hasRole(Roles.ADMIN);
    }

    /** ADMIN or COORDINATOR, whether that is the main role or an extra duty. */
    public boolean isStaff() {
        return hasRole(Roles.ADMIN) || hasRole(Roles.COORDINATOR);
    }

    /**
     * The student id this principal owns, or {@code null} when the caller is not a student.
     * Guards that enforce "students only see their own data" read this.
     */
    public Long studentIdOrNull() {
        return isStudent() ? profileId : null;
    }

    public Long trainerIdOrNull() {
        return isTrainer() ? profileId : null;
    }
}
