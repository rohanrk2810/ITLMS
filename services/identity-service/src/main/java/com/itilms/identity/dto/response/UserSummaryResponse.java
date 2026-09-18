package com.itilms.identity.dto.response;

import com.itilms.identity.entity.User;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The minimum another service needs to display a person: a name, a role, and a
 * way to contact them.
 *
 * <p>Used by the internal bulk-lookup endpoint. Keeping it separate from
 * {@link UserResponse} means an internal call does not hand eleven other
 * services fields like {@code mustChangePassword} that are none of their
 * business — and that they would then be tempted to depend on.
 */
@Schema(description = "Condensed user record for internal service lookups")
public record UserSummaryResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String role,
        String status,
        Long profileId
) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.fullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getProfileId());
    }
}
