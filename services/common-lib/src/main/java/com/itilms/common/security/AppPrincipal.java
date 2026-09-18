package com.itilms.common.security;

import java.io.Serializable;
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
 * @param userId    {@code users.id} in identity-service
 * @param email     login email, useful for audit trails
 * @param fullName  display name, so services can render "marked by ..." without a lookup
 * @param role      one of {@link Roles}
 * @param profileId student id for STUDENT, trainer id for TRAINER, {@code null} otherwise
 */
public record AppPrincipal(
        Long userId,
        String email,
        String fullName,
        String role,
        Long profileId
) implements Serializable {

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(Roles.authority(role)));
    }

    public boolean isStudent() {
        return Roles.STUDENT.equals(role);
    }

    public boolean isTrainer() {
        return Roles.TRAINER.equals(role);
    }

    public boolean isAdmin() {
        return Roles.ADMIN.equals(role);
    }

    public boolean isStaff() {
        return Roles.ADMIN.equals(role) || Roles.COORDINATOR.equals(role);
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
