package com.itilms.identity.service.impl;

import java.time.Instant;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.NotificationRequestedEvent;
import com.itilms.common.event.UserCreatedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.identity.config.IdentityProperties;
import com.itilms.identity.dto.request.ChangePasswordRequest;
import com.itilms.identity.dto.request.ForgotPasswordRequest;
import com.itilms.identity.dto.request.LoginRequest;
import com.itilms.identity.dto.request.RegisterRequest;
import com.itilms.identity.dto.request.ResetPasswordRequest;
import com.itilms.identity.dto.response.AuthResponse;
import com.itilms.identity.dto.response.SessionResponse;
import com.itilms.identity.dto.response.UserResponse;
import com.itilms.identity.entity.PasswordResetToken;
import com.itilms.identity.entity.RefreshToken;
import com.itilms.identity.entity.User;
import com.itilms.identity.entity.UserRole;
import com.itilms.identity.entity.UserStatus;
import com.itilms.identity.repository.PasswordResetTokenRepository;
import com.itilms.identity.repository.RefreshTokenRepository;
import com.itilms.identity.repository.UserRepository;
import com.itilms.identity.security.DeviceLabels;
import com.itilms.identity.security.JwtIssuer;
import com.itilms.identity.service.AuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String SERVICE_NAME = "identity-service";

    /** {@code revoke_reason} of a session ended because the same account signed in again. */
    static final String REASON_NEW_LOGIN = "NEW_LOGIN";
    static final String SIGNED_IN_ELSEWHERE =
            "You were signed out because this account signed in on another device.";

    /**
     * A real BCrypt hash of a value nobody knows. When an unknown identifier is
     * submitted we verify against this before failing, so a sign-in attempt for
     * a non-existent account takes about as long as one for a real account.
     * Without it, response time alone reveals which email addresses are
     * registered — a quiet way to harvest the institute's user list.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final EventPublisher events;
    private final IdentityProperties properties;

    // -----------------------------------------------------------------
    // Sign in
    // -----------------------------------------------------------------

    // These two record what a failed attempt did (the failure count and lockout, the
    // revocation after a replayed token) and then throw. Spring rolls back on any
    // RuntimeException by default, which would undo exactly that work - so the failures
    // below must commit. The test AuthServiceImplTest.Transactions pins this.
    @Override
    @Transactional(noRollbackFor = {BadCredentialsException.class, ForbiddenOperationException.class})
    public AuthResponse login(LoginRequest request, ClientMetadata metadata) {
        User user = userRepository.findByEmailOrPhone(request.identifier().trim()).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            log.debug("Sign-in attempt for unknown identifier");
            throw new BadCredentialsException("Invalid credentials");
        }

        if (user.isLocked()) {
            long minutes = Math.max(1, java.time.Duration.between(
                    Instant.now(), user.getLockedUntil()).toMinutes());
            throw new ForbiddenOperationException(
                    "Too many failed attempts. Try again in about %d minute(s).".formatted(minutes));
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean nowLocked = user.recordFailedLogin(
                    properties.getSecurity().getMaxFailedAttempts(),
                    properties.getSecurity().getLockoutDuration());
            userRepository.save(user);

            if (nowLocked) {
                log.warn("Account {} locked after {} failed attempts",
                        user.getId(), user.getFailedAttempts());
                events.audit(SERVICE_NAME, "ACCOUNT_LOCKED", "User", user.getId(), null,
                        java.util.Map.of("reason", "failed login threshold reached",
                                "attempts", user.getFailedAttempts()));
            }
            throw new BadCredentialsException("Invalid credentials");
        }

        // Status is checked only after the password is verified. Checking it
        // first would let anyone discover which addresses belong to blocked
        // accounts simply by observing a different error.
        if (!user.getStatus().canSignIn()) {
            throw new ForbiddenOperationException(
                    "This account is %s. Please contact the institute administrator."
                            .formatted(user.getStatus().name().toLowerCase()));
        }

        enforceSingleSession(user);

        user.recordSuccessfulLogin();
        userRepository.save(user);

        log.info("User {} ({}) signed in", user.getId(), user.getRole());
        return issueTokens(user, metadata);
    }

    // -----------------------------------------------------------------
    // Self-registration
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request, ClientMetadata metadata) {
        if (!properties.getSecurity().isSelfRegistrationEnabled()) {
            throw new ForbiddenOperationException(
                    "Online registration is not open. Please contact the institute to enrol.");
        }
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw DuplicateResourceException.of("account", "email", email);
        }
        if (userRepository.existsByPhone(request.phone().trim())) {
            throw DuplicateResourceException.of("account", "phone number", request.phone());
        }

        User user = User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(email)
                .phone(request.phone().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                // Self-registration only ever creates a student. See RegisterRequest.
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);
        log.info("Self-registration created student account {}", user.getId());

        // admission-service listens and opens a student profile; without that
        // the account exists but has no academic record attached to it.
        events.publishAfterCommit(KafkaTopics.USER_CREATED,
                UserCreatedEvent.selfRegistered(user.getId(), user.getEmail(), user.getPhone(),
                        user.fullName(), user.getRole().name()));

        events.publishAfterCommit(KafkaTopics.NOTIFICATION_REQUESTED,
                NotificationRequestedEvent.toUsersWithEmail(
                        java.util.List.of(user.getId()), "ACCOUNT_CREATED",
                        "Welcome to the institute",
                        "Your account is ready. Browse our courses and speak to a counselor to enrol.",
                        "/student/dashboard"));

        return issueTokens(user, metadata);
    }

    // -----------------------------------------------------------------
    // Refresh
    // -----------------------------------------------------------------

    @Override
    @Transactional(noRollbackFor = {BadCredentialsException.class, ForbiddenOperationException.class})
    public AuthResponse refresh(String refreshToken, ClientMetadata metadata) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException("A refresh token is required");
        }

        String hash = jwtIssuer.hash(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (stored.getRevokedAt() != null && REASON_NEW_LOGIN.equals(stored.getRevokeReason())) {
            // Not theft: this device was signed out because the account signed in elsewhere.
            // The newer session must survive, so nothing else is revoked here.
            throw new BadCredentialsException(SIGNED_IN_ELSEWHERE);
        }

        if (stored.getRevokedAt() != null) {
            // A token that was already spent is being presented again. Either
            // this is a stale retry or a copy is in circulation, and there is
            // no way to tell which holder is genuine - so end every session and
            // make both parties sign in again.
            log.warn("Revoked refresh token replayed for user {} - revoking all sessions", stored.getUserId());
            refreshTokenRepository.revokeAllForUser(stored.getUserId(), Instant.now());
            events.audit(SERVICE_NAME, "REFRESH_TOKEN_REUSE_DETECTED", "User", stored.getUserId(),
                    null, java.util.Map.of("action", "all sessions revoked"));
            throw new BadCredentialsException("This session is no longer valid. Please sign in again.");
        }

        if (!stored.isActive()) {
            throw new BadCredentialsException("Your session has expired. Please sign in again.");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (!user.getStatus().canSignIn()) {
            stored.revoke();
            refreshTokenRepository.save(stored);
            throw new ForbiddenOperationException("This account is no longer active");
        }

        // Rotate: the presented token dies here and a new one takes its place.
        String replacement = jwtIssuer.generateRefreshToken();
        stored.revoke();
        stored.setReplacedBy(jwtIssuer.hash(replacement));
        refreshTokenRepository.save(stored);

        RefreshToken issued = persistRefreshToken(user, replacement, metadata,
                stored.getSessionId(), stored.getSessionStartedAt());
        log.debug("Rotated refresh token {} -> {} for user {}",
                stored.getId(), issued.getId(), user.getId());

        return AuthResponse.of(jwtIssuer.issueAccessToken(user), replacement,
                jwtIssuer.accessTokenTtlSeconds(), UserResponse.from(user));
    }

    // -----------------------------------------------------------------
    // Sign out
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(jwtIssuer.hash(refreshToken))
                .ifPresent(token -> {
                    token.revoke("LOGOUT");
                    refreshTokenRepository.save(token);
                    log.debug("Signed out session {} for user {}", token.getId(), token.getUserId());
                });
        // Unknown tokens are ignored on purpose: signing out should always
        // appear to work, and reporting "no such session" tells a caller
        // whether a token they hold is real.
    }

    @Override
    @Transactional
    public void logoutEverywhere(Long userId) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        log.info("Revoked {} session(s) for user {}", revoked, userId);
    }

    // -----------------------------------------------------------------
    // Passwords
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Your current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("The new password must be different from the current one");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Every other session is ended. If the password was changed because it
        // may have been compromised, leaving the attacker's session alive would
        // defeat the point.
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());

        events.audit(SERVICE_NAME, "PASSWORD_CHANGED", "User", userId, null, null);
        events.notifyUsers(java.util.List.of(userId), "SECURITY",
                "Your password was changed",
                "If you did not do this, contact the institute administrator immediately.", null);

        log.info("Password changed for user {}; all sessions revoked", userId);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, ClientMetadata metadata) {
        String email = request.email().trim();
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user == null || !user.getStatus().canSignIn()) {
            // Return normally. The caller cannot tell whether an email was sent,
            // which is the whole point (see the interface contract).
            log.debug("Password reset requested for an unknown or inactive address");
            return;
        }

        long recent = passwordResetTokenRepository.countRecentRequests(
                user.getId(), Instant.now().minus(java.time.Duration.ofHours(1)));
        if (recent >= properties.getSecurity().getMaxResetRequestsPerHour()) {
            log.warn("Password reset rate limit reached for user {}", user.getId());
            return;
        }

        passwordResetTokenRepository.invalidateAllForUser(user.getId(), Instant.now());

        String rawToken = jwtIssuer.generateRefreshToken();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(jwtIssuer.hash(rawToken))
                .expiresAt(jwtIssuer.passwordResetExpiry())
                .requestedIp(metadata.ipAddress())
                .build());

        // The raw token travels only in the email body. It is never logged,
        // never returned in the HTTP response, and kept out of the action URL:
        // anything in the URL could end up stored as an in-app notification,
        // and a stored raw token undoes the point of keeping only its hash here.
        // notification-service builds the link from the metadata, in the email only.
        events.publishAfterCommit(KafkaTopics.NOTIFICATION_REQUESTED,
                new NotificationRequestedEvent(
                        com.itilms.common.event.DomainEvent.newId(), Instant.now(),
                        java.util.List.of(user.getId()), null, null,
                        "PASSWORD_RESET", "Reset your password",
                        "Use the link below to choose a new password. It expires in 2 hours.",
                        "/reset-password", true,
                        java.util.Map.of("resetToken", rawToken, "email", user.getEmail())));

        log.info("Password reset token issued for user {}", user.getId());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(jwtIssuer.hash(request.token()))
                .orElseThrow(() -> new BusinessRuleException(
                        "This reset link is not valid. Please request a new one."));

        if (!token.isUsable()) {
            throw new BusinessRuleException(
                    "This reset link has already been used or has expired. Please request a new one.");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", token.getUserId()));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        // A successful reset also clears a lockout: the person has just proved
        // control of the mailbox, so continuing to bar them serves no purpose.
        user.recordSuccessfulLogin();
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());

        events.audit(SERVICE_NAME, "PASSWORD_RESET", "User", user.getId(), null, null);
        log.info("Password reset completed for user {}", user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<SessionResponse> sessions(Long userId) {
        return refreshTokenRepository
                .findTop20ByUserIdAndReplacedByIsNullOrderBySessionStartedAtDesc(userId).stream()
                .map(SessionResponse::from)
                .toList();
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private AuthResponse issueTokens(User user, ClientMetadata metadata) {
        String refreshToken = jwtIssuer.generateRefreshToken();
        persistRefreshToken(user, refreshToken, metadata);
        return AuthResponse.of(
                jwtIssuer.issueAccessToken(user),
                refreshToken,
                jwtIssuer.accessTokenTtlSeconds(),
                UserResponse.from(user));
    }

    /**
     * The one-active-login rule, decided here on the server so it cannot be bypassed by
     * a modified client. Under REPLACE the older sessions end and the new login proceeds;
     * under DENY the new login is refused while another session is genuinely live (one
     * idle past the timeout no longer counts, or a closed browser would lock the user out
     * for the full refresh-token lifetime).
     *
     * <p>An already-issued access token stays valid until it expires (itilms.jwt.access-token-ttl),
     * because the other services verify it without calling back here. The old device cannot
     * refresh, so it is signed out at the latest one access-token lifetime later.
     */
    private void enforceSingleSession(User user) {
        var sessions = properties.getSecurity().getSessions();
        if (!sessions.appliesTo(user.getRole().name())) {
            return;
        }
        Instant now = Instant.now();
        var live = refreshTokenRepository.findLiveSessions(user.getId(), now);
        if (live.isEmpty()) {
            return;
        }
        if (sessions.getConflictPolicy() == IdentityProperties.Security.ConflictPolicy.DENY) {
            Instant cutoff = now.minus(sessions.getIdleTimeout());
            boolean blocked = live.stream().anyMatch(t -> t.getLastActivityAt().isAfter(cutoff));
            if (blocked) {
                throw new ForbiddenOperationException(
                        "This account is already signed in on another device. Sign out there first, "
                                + "or wait a few minutes if that device is no longer in use.");
            }
        }
        int ended = refreshTokenRepository.revokeAllForUser(user.getId(), now, REASON_NEW_LOGIN);
        log.info("User {} signed in again: ended {} earlier session(s)", user.getId(), ended);
        events.audit(SERVICE_NAME, "SESSION_REPLACED", "User", user.getId(), null,
                java.util.Map.of("endedSessions", ended));
    }

    private RefreshToken persistRefreshToken(User user, String rawToken, ClientMetadata metadata) {
        return persistRefreshToken(user, rawToken, metadata, java.util.UUID.randomUUID().toString(), Instant.now());
    }

    private RefreshToken persistRefreshToken(User user, String rawToken, ClientMetadata metadata,
                                             String sessionId, Instant startedAt) {
        return refreshTokenRepository.save(RefreshToken.builder()
                .sessionId(sessionId)
                .sessionStartedAt(startedAt)
                .lastActivityAt(Instant.now())
                .deviceLabel(DeviceLabels.of(metadata.userAgent()))
                .userId(user.getId())
                .tokenHash(jwtIssuer.hash(rawToken))
                .expiresAt(jwtIssuer.refreshTokenExpiry())
                .ipAddress(metadata.ipAddress())
                .userAgent(truncate(metadata.userAgent()))
                .build());
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 255 ? value.substring(0, 255) : value;
    }
}
