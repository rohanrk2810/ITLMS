package com.itilms.common.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.common.exception.ForbiddenOperationException;

/**
 * Access to the current caller from service code.
 *
 * <p>Services use this for the ownership checks the documentation demands in
 * Section 14 — a student may only read their own attendance, a trainer may only
 * evaluate submissions from their own batches. Those checks live in the service
 * layer on purpose: Doc S12 says "backend permission checks must not rely on
 * frontend visibility", and annotations alone cannot express "…belonging to me".
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AppPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AppPrincipal principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    public static AppPrincipal requirePrincipal() {
        return currentPrincipal().orElseThrow(
                () -> new ForbiddenOperationException("Authentication is required for this operation"));
    }

    public static Long currentUserId() {
        return currentPrincipal().map(AppPrincipal::userId).orElse(null);
    }

    public static String currentUserEmail() {
        return currentPrincipal().map(AppPrincipal::email).orElse("system");
    }

    /**
     * Enforces "this record belongs to the caller, or the caller is staff".
     *
     * @param ownerStudentId the student the record belongs to
     */
    public static void requireStudentOwnershipOrStaff(Long ownerStudentId) {
        AppPrincipal principal = requirePrincipal();
        if (principal.isStudent()) {
            if (ownerStudentId == null || !ownerStudentId.equals(principal.profileId())) {
                throw new ForbiddenOperationException("You may only access your own records");
            }
        }
    }

    /** Enforces "this batch is mine, or I am staff" for trainers. */
    public static void requireTrainerOwnershipOrStaff(Long ownerTrainerId) {
        AppPrincipal principal = requirePrincipal();
        if (principal.isTrainer()) {
            if (ownerTrainerId == null || !ownerTrainerId.equals(principal.profileId())) {
                throw new ForbiddenOperationException("You may only access batches assigned to you");
            }
        }
    }
}
