package com.itilms.identity.dto.response;

import java.time.Instant;

import com.itilms.identity.entity.User;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A user as the API presents them.
 *
 * <p>Built by hand from the entity rather than serialising it. The entity holds
 * a BCrypt hash, a lockout timestamp and a failed-attempt counter; none of those
 * belong in an HTTP response, and a DTO makes leaking them impossible rather
 * than merely unlikely.
 */
@Schema(description = "User account")
public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phone,
        String role,
        String roleDisplayName,
        String status,
        @Schema(description = "Student or trainer id, when this user has such a profile")
        Long profileId,
        String profileCode,
        boolean mustChangePassword,
        Instant lastLoginAt,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.fullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.getRole().displayName(),
                user.getStatus().name(),
                user.getProfileId(),
                user.getProfileCode(),
                user.isMustChangePassword(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
