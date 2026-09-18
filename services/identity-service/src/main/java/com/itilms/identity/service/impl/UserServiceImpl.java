package com.itilms.identity.service.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.NotificationRequestedEvent;
import com.itilms.common.event.UserCreatedEvent;
import com.itilms.common.event.UserStatusChangedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.SecurityUtils;
import com.itilms.common.util.Codes;
import com.itilms.identity.dto.request.CreateUserRequest;
import com.itilms.identity.dto.request.UpdateStatusRequest;
import com.itilms.identity.dto.request.UpdateUserRequest;
import com.itilms.identity.dto.response.UserResponse;
import com.itilms.identity.dto.response.UserSummaryResponse;
import com.itilms.identity.entity.User;
import com.itilms.identity.entity.UserRole;
import com.itilms.identity.entity.UserStatus;
import com.itilms.identity.repository.RefreshTokenRepository;
import com.itilms.identity.repository.UserRepository;
import com.itilms.identity.specification.UserSpecifications;
import com.itilms.identity.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String SERVICE_NAME = "identity-service";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String role, String status, String query, Pageable pageable) {
        Specification<User> spec = Specification.allOf(
                UserSpecifications.hasRole(role),
                UserSpecifications.hasStatus(status),
                UserSpecifications.matches(query));

        return PageResponse.from(userRepository.findAll(spec, pageable), UserResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw DuplicateResourceException.of("user", "email", email);
        }

        String phone = blankToNull(request.phone());
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw DuplicateResourceException.of("user", "phone number", phone);
        }

        UserRole role = UserRole.of(request.role());

        // A blank password is the normal case for staff-created accounts: the
        // service invents one, the holder is forced to replace it, and nobody
        // has to think up (or write down) a password for someone else.
        boolean generated = request.password() == null || request.password().isBlank();
        String rawPassword = generated ? Codes.temporaryPassword() : request.password();

        User user = userRepository.save(User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(generated)
                .build());

        log.info("Created {} account {} ({})", role, user.getId(), user.getEmail());

        events.publishAfterCommit(KafkaTopics.USER_CREATED,
                UserCreatedEvent.of(user.getId(), user.getEmail(), user.getPhone(),
                        user.fullName(), role.name()));

        events.audit(SERVICE_NAME, "USER_CREATED", "User", user.getId(), null,
                Map.of("email", user.getEmail(), "role", role.name()));

        // The temporary password is delivered by email only. Returning it in
        // the HTTP response would put a working credential into browser history,
        // proxy logs and whatever the client happens to cache.
        if (generated) {
            events.publishAfterCommit(KafkaTopics.NOTIFICATION_REQUESTED,
                    new NotificationRequestedEvent(
                            com.itilms.common.event.DomainEvent.newId(), Instant.now(),
                            List.of(user.getId()), null, null,
                            "ACCOUNT_CREATED", "Your IT-ILMS account is ready",
                            "Sign in with your email and the temporary password below, "
                                    + "then choose a password of your own.",
                            "/login", true,
                            Map.of("temporaryPassword", rawPassword, "email", user.getEmail())));
        }

        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailExcluding(email, id)) {
            throw DuplicateResourceException.of("user", "email", email);
        }
        String phone = blankToNull(request.phone());
        if (phone != null && userRepository.existsByPhoneExcluding(phone, id)) {
            throw DuplicateResourceException.of("user", "phone number", phone);
        }

        var before = Map.of("email", user.getEmail(),
                "phone", String.valueOf(user.getPhone()),
                "name", user.fullName());

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        userRepository.save(user);

        events.audit(SERVICE_NAME, "USER_UPDATED", "User", id, before,
                Map.of("email", user.getEmail(),
                        "phone", String.valueOf(user.getPhone()),
                        "name", user.fullName()));

        // Other services cache the display name; tell them it moved.
        events.publishAfterCommit(KafkaTopics.USER_UPDATED,
                UserCreatedEvent.of(user.getId(), user.getEmail(), user.getPhone(),
                        user.fullName(), user.getRole().name()));

        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public UserResponse updateStatus(Long id, UpdateStatusRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        UserStatus target = UserStatus.valueOf(request.status().toUpperCase());
        UserStatus previous = user.getStatus();
        if (previous == target) {
            return UserResponse.from(user);
        }

        // Locking yourself out of the only admin account is an easy mistake and
        // an expensive one - it needs a database edit to undo.
        Long actingUserId = SecurityUtils.currentUserId();
        if (user.getId().equals(actingUserId) && target != UserStatus.ACTIVE) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }
        if (user.getRole() == UserRole.ADMIN && target != UserStatus.ACTIVE) {
            long remainingAdmins = userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
            if (remainingAdmins <= 1) {
                throw new BusinessRuleException(
                        "This is the last active administrator. Promote another user before deactivating it.");
            }
        }

        user.setStatus(target);
        if (target == UserStatus.ACTIVE) {
            // Reactivating clears any brute-force lockout left over from before.
            user.recordSuccessfulLogin();
            user.setLastLoginAt(null);
        }
        userRepository.save(user);

        if (!target.canSignIn()) {
            // Blocking an account has to end its live sessions too, or the user
            // keeps working until their access token happens to expire.
            refreshTokenRepository.revokeAllForUser(id, Instant.now());
        }

        events.publishAfterCommit(KafkaTopics.USER_STATUS_CHANGED,
                UserStatusChangedEvent.of(id, user.getEmail(), previous.name(),
                        target.name(), actingUserId));

        events.audit(SERVICE_NAME, "USER_STATUS_CHANGED", "User", id,
                Map.of("status", previous.name()),
                Map.of("status", target.name(), "reason", request.reason()));

        log.info("User {} status {} -> {} ({})", id, previous, target, request.reason());
        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public void resetPasswordAsAdmin(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        String temporary = Codes.temporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(temporary));
        user.setMustChangePassword(true);
        user.recordSuccessfulLogin();
        user.setLastLoginAt(null);
        userRepository.save(user);

        refreshTokenRepository.revokeAllForUser(id, Instant.now());

        events.publishAfterCommit(KafkaTopics.NOTIFICATION_REQUESTED,
                new NotificationRequestedEvent(
                        com.itilms.common.event.DomainEvent.newId(), Instant.now(),
                        List.of(id), null, null,
                        "PASSWORD_RESET", "Your password was reset by an administrator",
                        "Sign in with the temporary password below and choose a new one.",
                        "/login", true,
                        Map.of("temporaryPassword", temporary, "email", user.getEmail())));

        events.audit(SERVICE_NAME, "PASSWORD_RESET_BY_ADMIN", "User", id, null, null);
        log.info("Administrator reset the password for user {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > 500) {
            throw new BusinessRuleException("At most 500 user ids may be resolved in one call");
        }
        return userRepository.findByIdIn(ids).stream().map(UserSummaryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> findActiveByRole(String role) {
        return userRepository.findByRoleAndStatus(UserRole.of(role), UserStatus.ACTIVE)
                .stream().map(UserSummaryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsByRole() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (UserRole role : UserRole.values()) {
            counts.put(role.name(), userRepository.countByRoleAndStatus(role, UserStatus.ACTIVE));
        }
        counts.put("TOTAL_ACTIVE", userRepository.countByStatus(UserStatus.ACTIVE));
        counts.put("TOTAL_INACTIVE", userRepository.countByStatus(UserStatus.INACTIVE));
        counts.put("TOTAL_BLOCKED", userRepository.countByStatus(UserStatus.BLOCKED));
        return counts;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
