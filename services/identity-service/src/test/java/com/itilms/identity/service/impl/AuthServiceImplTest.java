package com.itilms.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.NotificationRequestedEvent;
import com.itilms.common.event.UserCreatedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.identity.config.IdentityProperties;
import com.itilms.identity.dto.request.ChangePasswordRequest;
import com.itilms.identity.dto.request.ForgotPasswordRequest;
import com.itilms.identity.dto.request.LoginRequest;
import com.itilms.identity.dto.request.RegisterRequest;
import com.itilms.identity.dto.request.ResetPasswordRequest;
import com.itilms.identity.dto.response.AuthResponse;
import com.itilms.identity.entity.PasswordResetToken;
import com.itilms.identity.entity.RefreshToken;
import com.itilms.identity.entity.User;
import com.itilms.identity.entity.UserRole;
import com.itilms.identity.entity.UserStatus;
import com.itilms.identity.repository.PasswordResetTokenRepository;
import com.itilms.identity.repository.RefreshTokenRepository;
import com.itilms.identity.repository.UserRepository;
import com.itilms.identity.security.JwtIssuer;
import com.itilms.identity.service.AuthService.ClientMetadata;

/**
 * The sign-in rules that keep accounts safe: lockout, refresh-token rotation and
 * replay detection, single-use reset links, and closed self-registration.
 *
 * <p>Repositories are mocked, so nothing here talks to a database. The one thing
 * a mock cannot show is whether a failed attempt is <em>committed</em>, so the
 * {@link Transactions} tests ask Spring itself which exceptions roll a method back.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final ClientMetadata CLIENT = new ClientMetadata("10.0.0.7", "JUnit");

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtIssuer jwtIssuer;
    @Mock EventPublisher events;

    IdentityProperties properties = new IdentityProperties();
    AuthServiceImpl service;

    private int tokenCounter;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, refreshTokenRepository,
                passwordResetTokenRepository, passwordEncoder, jwtIssuer, events, properties);

        // A hash that is visibly not the raw value, so a test can tell which one was stored.
        lenient().when(jwtIssuer.hash(anyString())).thenAnswer(i -> "hash:" + i.getArgument(0));
        lenient().when(jwtIssuer.generateRefreshToken()).thenAnswer(i -> "raw-" + (++tokenCounter));
        lenient().when(jwtIssuer.issueAccessToken(any())).thenReturn("access-jwt");
        lenient().when(jwtIssuer.accessTokenTtlSeconds()).thenReturn(900L);
        lenient().when(jwtIssuer.refreshTokenExpiry()).thenReturn(Instant.now().plus(Duration.ofDays(7)));
        lenient().when(jwtIssuer.passwordResetExpiry()).thenReturn(Instant.now().plus(Duration.ofHours(2)));
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static User user(long id, UserStatus status) {
        return User.builder().id(id).firstName("Asha").lastName("Patil").email("asha@test.local")
                .phone("9800000001").passwordHash("stored-hash").role(UserRole.STUDENT).status(status).build();
    }

    private static RefreshToken storedToken(long userId, String raw) {
        return RefreshToken.builder().userId(userId).tokenHash("hash:" + raw)
                .expiresAt(Instant.now().plus(Duration.ofDays(1))).build();
    }

    private static PasswordResetToken resetToken(long userId, String raw) {
        return PasswordResetToken.builder().userId(userId).tokenHash("hash:" + raw)
                .expiresAt(Instant.now().plus(Duration.ofHours(1))).build();
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Sign in")
    class Login {

        @Test
        void correctPasswordIssuesTokensAndStoresOnlyTheHashOfTheRefreshToken() {
            User user = user(1, UserStatus.ACTIVE);
            user.setFailedAttempts(3);
            when(userRepository.findByEmailOrPhone("asha@test.local")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Passw0rd!", "stored-hash")).thenReturn(true);

            AuthResponse response = service.login(new LoginRequest("asha@test.local", "Passw0rd!"), CLIENT);

            assertThat(response.accessToken()).isEqualTo("access-jwt");
            assertThat(response.refreshToken()).isEqualTo("raw-1");
            assertThat(user.getFailedAttempts()).as("a good sign-in clears earlier failures").isZero();
            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(saved.capture());
            assertThat(saved.getValue().getTokenHash()).isEqualTo("hash:raw-1").isNotEqualTo("raw-1");
            assertThat(saved.getValue().getIpAddress()).isEqualTo("10.0.0.7");
        }

        @Test
        void identifierIsTrimmedBeforeLookup() {
            User user = user(1, UserStatus.ACTIVE);
            when(userRepository.findByEmailOrPhone("asha@test.local")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), eq("stored-hash"))).thenReturn(true);

            service.login(new LoginRequest("  asha@test.local  ", "x"), CLIENT);

            verify(userRepository).findByEmailOrPhone("asha@test.local");
        }

        @Test
        void unknownAccountStillPaysForAHashCheckAndGetsTheSameError() {
            when(userRepository.findByEmailOrPhone("nobody@test.local")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.login(new LoginRequest("nobody@test.local", "pw"), CLIENT))
                    .isInstanceOf(BadCredentialsException.class).hasMessage("Invalid credentials");

            // Same work as a real account, so response time does not reveal who is registered.
            verify(passwordEncoder).matches(eq("pw"), anyString());
            verify(userRepository, never()).save(any());
        }

        @Test
        void wrongPasswordCountsAsAFailedAttempt() {
            User user = user(1, UserStatus.ACTIVE);
            when(userRepository.findByEmailOrPhone("asha@test.local")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

            assertThatThrownBy(() -> service.login(new LoginRequest("asha@test.local", "wrong"), CLIENT))
                    .isInstanceOf(BadCredentialsException.class);

            assertThat(user.getFailedAttempts()).isEqualTo(1);
            assertThat(user.isLocked()).isFalse();
            verify(userRepository).save(user);
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        void fifthWrongPasswordLocksTheAccountAndIsAudited() {
            User user = user(1, UserStatus.ACTIVE);
            user.setFailedAttempts(4);
            when(userRepository.findByEmailOrPhone("asha@test.local")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

            assertThatThrownBy(() -> service.login(new LoginRequest("asha@test.local", "wrong"), CLIENT))
                    .isInstanceOf(BadCredentialsException.class);

            assertThat(user.isLocked()).isTrue();
            assertThat(user.getLockedUntil()).isAfter(Instant.now().plus(Duration.ofMinutes(14)));
            verify(events).audit(eq("identity-service"), eq("ACCOUNT_LOCKED"), eq("User"), eq(1L), any(), any());
        }

        @Test
        void lockedAccountIsRefusedEvenWithTheRightPasswordAndThePasswordIsNotChecked() {
            User user = user(1, UserStatus.ACTIVE);
            user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));
            when(userRepository.findByEmailOrPhone("asha@test.local")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.login(new LoginRequest("asha@test.local", "Passw0rd!"), CLIENT))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("Too many failed attempts");

            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        void aLockThatHasExpiredNoLongerBlocksSignIn() {
            User user = user(1, UserStatus.ACTIVE);
            user.setFailedAttempts(5);
            user.setLockedUntil(Instant.now().minusSeconds(5));
            when(userRepository.findByEmailOrPhone("asha@test.local")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Passw0rd!", "stored-hash")).thenReturn(true);

            AuthResponse response = service.login(new LoginRequest("asha@test.local", "Passw0rd!"), CLIENT);

            assertThat(response.accessToken()).isNotNull();
            assertThat(user.getFailedAttempts()).isZero();
            assertThat(user.getLockedUntil()).isNull();
        }

        @Test
        void blockedAccountWithTheRightPasswordIsToldWhy() {
            User user = user(1, UserStatus.BLOCKED);
            when(userRepository.findByEmailOrPhone("asha@test.local")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Passw0rd!", "stored-hash")).thenReturn(true);

            assertThatThrownBy(() -> service.login(new LoginRequest("asha@test.local", "Passw0rd!"), CLIENT))
                    .isInstanceOf(ForbiddenOperationException.class).hasMessageContaining("blocked");
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        void blockedAccountWithAWrongPasswordGetsNoHintAboutItsStatus() {
            User user = user(1, UserStatus.BLOCKED);
            when(userRepository.findByEmailOrPhone("asha@test.local")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

            assertThatThrownBy(() -> service.login(new LoginRequest("asha@test.local", "wrong"), CLIENT))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Refresh token rotation")
    class Refresh {

        @Test
        void aValidTokenIsSpentAndReplacedByANewOne() {
            RefreshToken old = storedToken(1, "old");
            when(refreshTokenRepository.findByTokenHash("hash:old")).thenReturn(Optional.of(old));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1, UserStatus.ACTIVE)));

            AuthResponse response = service.refresh("old", CLIENT);

            assertThat(response.refreshToken()).isEqualTo("raw-1");
            assertThat(old.getRevokedAt()).as("the presented token is dead").isNotNull();
            assertThat(old.getReplacedBy()).isEqualTo("hash:raw-1");
            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(saved.capture());
            RefreshToken issued = saved.getAllValues().get(1);
            assertThat(issued.getTokenHash()).isEqualTo("hash:raw-1");
            assertThat(issued.getRevokedAt()).isNull();
        }

        @Test
        void unknownTokenIsRefused() {
            when(refreshTokenRepository.findByTokenHash("hash:nope")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refresh("nope", CLIENT)).isInstanceOf(BadCredentialsException.class);
            verify(refreshTokenRepository, never()).revokeAllForUser(anyLong(), any());
        }

        @Test
        void missingTokenIsRefusedWithoutLookingAnythingUp() {
            assertThatThrownBy(() -> service.refresh(null, CLIENT)).isInstanceOf(BadCredentialsException.class);
            assertThatThrownBy(() -> service.refresh("  ", CLIENT)).isInstanceOf(BadCredentialsException.class);
            verify(refreshTokenRepository, never()).findByTokenHash(any());
        }

        @Test
        void replayingASpentTokenEndsEveryOfThatUsersSessions() {
            RefreshToken spent = storedToken(1, "spent");
            spent.revoke();
            when(refreshTokenRepository.findByTokenHash("hash:spent")).thenReturn(Optional.of(spent));

            assertThatThrownBy(() -> service.refresh("spent", CLIENT))
                    .isInstanceOf(BadCredentialsException.class).hasMessageContaining("sign in again");

            verify(refreshTokenRepository).revokeAllForUser(eq(1L), any(Instant.class));
            verify(events).audit(eq("identity-service"), eq("REFRESH_TOKEN_REUSE_DETECTED"), eq("User"), eq(1L), any(), any());
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        void usingTheSameTokenTwiceRotatesOnceThenTriggersReuseDetection() {
            RefreshToken stored = storedToken(1, "once");
            when(refreshTokenRepository.findByTokenHash("hash:once")).thenReturn(Optional.of(stored));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1, UserStatus.ACTIVE)));

            service.refresh("once", CLIENT);
            verify(refreshTokenRepository, never()).revokeAllForUser(anyLong(), any());

            assertThatThrownBy(() -> service.refresh("once", CLIENT)).isInstanceOf(BadCredentialsException.class);
            verify(refreshTokenRepository).revokeAllForUser(eq(1L), any(Instant.class));
        }

        @Test
        void anExpiredTokenIsRefusedButDoesNotKillOtherSessions() {
            RefreshToken expired = RefreshToken.builder().userId(1L).tokenHash("hash:old")
                    .expiresAt(Instant.now().minusSeconds(60)).build();
            when(refreshTokenRepository.findByTokenHash("hash:old")).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> service.refresh("old", CLIENT))
                    .isInstanceOf(BadCredentialsException.class).hasMessageContaining("expired");
            verify(refreshTokenRepository, never()).revokeAllForUser(anyLong(), any());
        }

        @Test
        void aBlockedUserCannotRefreshAndTheTokenIsRevoked() {
            RefreshToken stored = storedToken(1, "tok");
            when(refreshTokenRepository.findByTokenHash("hash:tok")).thenReturn(Optional.of(stored));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1, UserStatus.BLOCKED)));

            assertThatThrownBy(() -> service.refresh("tok", CLIENT)).isInstanceOf(ForbiddenOperationException.class);

            assertThat(stored.getRevokedAt()).isNotNull();
            verify(refreshTokenRepository).save(stored);
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Password reset")
    class Reset {

        @Test
        void unknownAddressGetsNoTokenAndNoDifferentAnswer() {
            when(userRepository.findByEmailIgnoreCase("ghost@test.local")).thenReturn(Optional.empty());

            service.forgotPassword(new ForgotPasswordRequest("ghost@test.local"), CLIENT);

            verify(passwordResetTokenRepository, never()).save(any());
            verify(events, never()).publishAfterCommit(anyString(), any());
        }

        @Test
        void inactiveAccountGetsNoToken() {
            when(userRepository.findByEmailIgnoreCase("asha@test.local")).thenReturn(Optional.of(user(1, UserStatus.BLOCKED)));

            service.forgotPassword(new ForgotPasswordRequest("asha@test.local"), CLIENT);

            verify(passwordResetTokenRepository, never()).save(any());
        }

        @Test
        void aRequestIssuesOneTokenStoredOnlyAsAHashAndEmailedRaw() {
            when(userRepository.findByEmailIgnoreCase("asha@test.local")).thenReturn(Optional.of(user(1, UserStatus.ACTIVE)));
            when(passwordResetTokenRepository.countRecentRequests(eq(1L), any())).thenReturn(0L);

            service.forgotPassword(new ForgotPasswordRequest(" asha@test.local "), CLIENT);

            verify(passwordResetTokenRepository).invalidateAllForUser(eq(1L), any(Instant.class));
            ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(passwordResetTokenRepository).save(saved.capture());
            assertThat(saved.getValue().getTokenHash()).isEqualTo("hash:raw-1");

            ArgumentCaptor<NotificationRequestedEvent> event = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
            verify(events).publishAfterCommit(eq(KafkaTopics.NOTIFICATION_REQUESTED), event.capture());
            assertThat(event.getValue().metadata()).containsEntry("resetToken", "raw-1");
            assertThat(event.getValue().actionUrl()).as("the raw token never goes in a URL that might be stored")
                    .doesNotContain("raw-1");
        }

        @Test
        void aFourthRequestInAnHourIsSilentlyDropped() {
            when(userRepository.findByEmailIgnoreCase("asha@test.local")).thenReturn(Optional.of(user(1, UserStatus.ACTIVE)));
            when(passwordResetTokenRepository.countRecentRequests(eq(1L), any())).thenReturn(3L);

            service.forgotPassword(new ForgotPasswordRequest("asha@test.local"), CLIENT);

            verify(passwordResetTokenRepository, never()).save(any());
            verify(events, never()).publishAfterCommit(anyString(), any());
        }

        @Test
        void aValidTokenSetsTheNewPasswordAndEndsAllSessions() {
            User user = user(1, UserStatus.ACTIVE);
            user.setMustChangePassword(true);
            user.setFailedAttempts(5);
            user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));
            PasswordResetToken token = resetToken(1, "reset");
            when(passwordResetTokenRepository.findByTokenHash("hash:reset")).thenReturn(Optional.of(token));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("NewPassw0rd!")).thenReturn("new-hash");

            service.resetPassword(new ResetPasswordRequest("reset", "NewPassw0rd!"));

            assertThat(user.getPasswordHash()).isEqualTo("new-hash");
            assertThat(user.isMustChangePassword()).isFalse();
            assertThat(user.isLocked()).as("proving control of the mailbox clears a lockout").isFalse();
            assertThat(token.getUsedAt()).isNotNull();
            verify(refreshTokenRepository).revokeAllForUser(eq(1L), any(Instant.class));
            verify(events).audit(eq("identity-service"), eq("PASSWORD_RESET"), eq("User"), eq(1L), any(), any());
        }

        @Test
        void aResetLinkWorksOnceOnly() {
            PasswordResetToken token = resetToken(1, "reset");
            when(passwordResetTokenRepository.findByTokenHash("hash:reset")).thenReturn(Optional.of(token));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1, UserStatus.ACTIVE)));
            when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

            service.resetPassword(new ResetPasswordRequest("reset", "NewPassw0rd!"));

            assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("reset", "Another1!")))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already been used");
            verify(passwordEncoder).encode(anyString());
        }

        @Test
        void anExpiredLinkIsRefusedAndChangesNothing() {
            PasswordResetToken token = PasswordResetToken.builder().userId(1L).tokenHash("hash:late")
                    .expiresAt(Instant.now().minusSeconds(1)).build();
            when(passwordResetTokenRepository.findByTokenHash("hash:late")).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("late", "NewPassw0rd!")))
                    .isInstanceOf(BusinessRuleException.class);
            verify(userRepository, never()).save(any());
            verify(refreshTokenRepository, never()).revokeAllForUser(anyLong(), any());
        }

        @Test
        void aMadeUpTokenIsRefused() {
            when(passwordResetTokenRepository.findByTokenHash("hash:guess")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("guess", "NewPassw0rd!")))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not valid");
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Changing a password while signed in")
    class ChangePassword {

        @Test
        void wrongCurrentPasswordIsRefused() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1, UserStatus.ACTIVE)));
            when(passwordEncoder.matches("nope", "stored-hash")).thenReturn(false);

            assertThatThrownBy(() -> service.changePassword(1L, new ChangePasswordRequest("nope", "NewPassw0rd!")))
                    .isInstanceOf(BadCredentialsException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        void theNewPasswordMustDifferFromTheOld() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1, UserStatus.ACTIVE)));
            when(passwordEncoder.matches(anyString(), eq("stored-hash"))).thenReturn(true);

            assertThatThrownBy(() -> service.changePassword(1L, new ChangePasswordRequest("Same1234!", "Same1234!")))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void successClearsTheForcedChangeFlagAndEndsOtherSessions() {
            User user = user(1, UserStatus.ACTIVE);
            user.setMustChangePassword(true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Old12345!", "stored-hash")).thenReturn(true);
            when(passwordEncoder.matches("NewPassw0rd!", "stored-hash")).thenReturn(false);
            when(passwordEncoder.encode("NewPassw0rd!")).thenReturn("new-hash");

            service.changePassword(1L, new ChangePasswordRequest("Old12345!", "NewPassw0rd!"));

            assertThat(user.getPasswordHash()).isEqualTo("new-hash");
            assertThat(user.isMustChangePassword()).isFalse();
            verify(refreshTokenRepository).revokeAllForUser(eq(1L), any(Instant.class));
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Self-registration")
    class SelfRegistration {

        private final RegisterRequest request =
                new RegisterRequest("Ravi", "Kumar", " Ravi@Test.Local ", "9800000002", "Passw0rd!");

        @Test
        void isClosedByDefaultAndCreatesNothing() {
            assertThat(properties.getSecurity().isSelfRegistrationEnabled()).isFalse();

            assertThatThrownBy(() -> service.register(request, CLIENT))
                    .isInstanceOf(ForbiddenOperationException.class).hasMessageContaining("not open");

            verify(userRepository, never()).save(any());
            verify(events, never()).publishAfterCommit(anyString(), any());
        }

        @Test
        void whenOpenedItCreatesAStudentWithAHashedPasswordAndAnnouncesIt() {
            properties.getSecurity().setSelfRegistrationEnabled(true);
            when(userRepository.existsByEmailIgnoreCase("ravi@test.local")).thenReturn(false);
            when(userRepository.existsByPhone("9800000002")).thenReturn(false);
            when(passwordEncoder.encode("Passw0rd!")).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenAnswer(i -> {
                User u = i.getArgument(0);
                u.setId(9L);
                return u;
            });

            AuthResponse response = service.register(request, CLIENT);

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            assertThat(saved.getValue().getRole()).isEqualTo(UserRole.STUDENT);
            assertThat(saved.getValue().getEmail()).isEqualTo("ravi@test.local");
            assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded");
            assertThat(response.accessToken()).isEqualTo("access-jwt");
            verify(events).publishAfterCommit(eq(KafkaTopics.USER_CREATED), any(UserCreatedEvent.class));
        }

        @Test
        void aDuplicateEmailOrPhoneIsRefused() {
            properties.getSecurity().setSelfRegistrationEnabled(true);
            when(userRepository.existsByEmailIgnoreCase("ravi@test.local")).thenReturn(true);
            assertThatThrownBy(() -> service.register(request, CLIENT)).isInstanceOf(DuplicateResourceException.class);

            when(userRepository.existsByEmailIgnoreCase("ravi@test.local")).thenReturn(false);
            when(userRepository.existsByPhone("9800000002")).thenReturn(true);
            assertThatThrownBy(() -> service.register(request, CLIENT)).isInstanceOf(DuplicateResourceException.class);

            verify(userRepository, never()).save(any());
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Sign out")
    class Logout {

        @Test
        void revokesTheSessionAndIgnoresUnknownOrBlankTokens() {
            RefreshToken stored = storedToken(1, "mine");
            when(refreshTokenRepository.findByTokenHash("hash:mine")).thenReturn(Optional.of(stored));
            when(refreshTokenRepository.findByTokenHash("hash:other")).thenReturn(Optional.empty());

            service.logout("mine");
            service.logout("other");
            service.logout(null);

            assertThat(stored.getRevokedAt()).isNotNull();
            verify(refreshTokenRepository).save(stored);
        }
    }

    // ------------------------------------------------------------------------------------

    /**
     * A failed sign-in has to be committed or it never counts. Spring rolls a
     * transaction back on any RuntimeException by default, and BadCredentialsException is
     * one; so a method that records the failure and then throws must opt out of the
     * rollback, or the counter, the lockout and the replay revocation all vanish.
     */
    @Nested
    @DisplayName("Failures that must still be committed")
    class Transactions {

        private final AnnotationTransactionAttributeSource rules = new AnnotationTransactionAttributeSource();

        private boolean rollsBackOn(String method, Class<?>[] args, RuntimeException failure) throws Exception {
            Method m = AuthServiceImpl.class.getMethod(method, args);
            return rules.getTransactionAttribute(m, AuthServiceImpl.class).rollbackOn(failure);
        }

        @Test
        void aWrongPasswordIsCommittedSoTheFailureCountAndLockoutSurvive() throws Exception {
            Class<?>[] args = {LoginRequest.class, ClientMetadata.class};
            assertThat(rollsBackOn("login", args, new BadCredentialsException("x")))
                    .as("login must not roll back on BadCredentialsException").isFalse();
        }

        @Test
        void aReplayedRefreshTokenIsCommittedSoTheRevocationOfAllSessionsSurvives() throws Exception {
            Class<?>[] args = {String.class, ClientMetadata.class};
            assertThat(rollsBackOn("refresh", args, new BadCredentialsException("x")))
                    .as("refresh must not roll back on BadCredentialsException").isFalse();
        }

        @Test
        void aRefreshByABlockedUserIsCommittedSoTheTokenStaysRevoked() throws Exception {
            Class<?>[] args = {String.class, ClientMetadata.class};
            assertThat(rollsBackOn("refresh", args, new ForbiddenOperationException("x")))
                    .as("refresh must not roll back on ForbiddenOperationException").isFalse();
        }

        @Test
        void unexpectedErrorsStillRollBack() throws Exception {
            Class<?>[] args = {LoginRequest.class, ClientMetadata.class};
            assertThat(rollsBackOn("login", args, new IllegalStateException("boom"))).isTrue();
        }
    }

    @Nested
    @DisplayName("One active login per user")
    class SingleSession {

        private User signedInUser() {
            User user = user(1, UserStatus.ACTIVE);
            when(userRepository.findByEmailOrPhone("asha@test.local")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Passw0rd!", "stored-hash")).thenReturn(true);
            return user;
        }

        private RefreshToken liveSession(Instant lastActivity) {
            RefreshToken t = storedToken(1, "old");
            t.setLastActivityAt(lastActivity);
            return t;
        }

        private void login() {
            service.login(new LoginRequest("asha@test.local", "Passw0rd!"), CLIENT);
        }

        @Test
        void newLoginEndsTheEarlierSessionUnderReplace() {
            signedInUser();
            when(refreshTokenRepository.findLiveSessions(eq(1L), any()))
                    .thenReturn(java.util.List.of(liveSession(Instant.now())));
            when(refreshTokenRepository.revokeAllForUser(eq(1L), any(), eq("NEW_LOGIN"))).thenReturn(1);

            login();

            verify(refreshTokenRepository).revokeAllForUser(eq(1L), any(), eq("NEW_LOGIN"));
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        void firstLoginRevokesNothing() {
            signedInUser();
            when(refreshTokenRepository.findLiveSessions(eq(1L), any())).thenReturn(java.util.List.of());

            login();

            verify(refreshTokenRepository, never()).revokeAllForUser(anyLong(), any(), anyString());
        }

        @Test
        void denyPolicyRefusesWhileAnotherSessionIsInUse() {
            properties.getSecurity().getSessions()
                    .setConflictPolicy(IdentityProperties.Security.ConflictPolicy.DENY);
            signedInUser();
            when(refreshTokenRepository.findLiveSessions(eq(1L), any()))
                    .thenReturn(java.util.List.of(liveSession(Instant.now())));

            assertThatThrownBy(this::login).isInstanceOf(ForbiddenOperationException.class);
            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
            verify(refreshTokenRepository, never()).revokeAllForUser(anyLong(), any(), anyString());
        }

        @Test
        void denyPolicyIgnoresASessionIdlePastTheTimeout() {
            properties.getSecurity().getSessions()
                    .setConflictPolicy(IdentityProperties.Security.ConflictPolicy.DENY);
            signedInUser();
            when(refreshTokenRepository.findLiveSessions(eq(1L), any()))
                    .thenReturn(java.util.List.of(liveSession(Instant.now().minus(Duration.ofHours(3)))));

            login();

            verify(refreshTokenRepository).revokeAllForUser(eq(1L), any(), eq("NEW_LOGIN"));
        }

        @Test
        void roleOutsideThePolicyMayKeepSeveralSessions() {
            properties.getSecurity().getSessions().setSingleSessionRoles(java.util.List.of("ADMIN"));
            signedInUser(); // a STUDENT

            login();

            verify(refreshTokenRepository, never()).findLiveSessions(anyLong(), any());
            verify(refreshTokenRepository, never()).revokeAllForUser(anyLong(), any(), anyString());
        }

        @Test
        void replayOfATokenEndedByANewLoginDoesNotKillTheNewSession() {
            RefreshToken old = storedToken(1, "old");
            old.revoke("NEW_LOGIN");
            when(refreshTokenRepository.findByTokenHash("hash:old")).thenReturn(Optional.of(old));

            assertThatThrownBy(() -> service.refresh("old", CLIENT))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("another device");
            verify(refreshTokenRepository, never()).revokeAllForUser(anyLong(), any());
        }

        @Test
        void rotationKeepsTheSessionIdentity() {
            RefreshToken old = storedToken(1, "old");
            old.setSessionId("sess-1");
            when(refreshTokenRepository.findByTokenHash("hash:old")).thenReturn(Optional.of(old));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1, UserStatus.ACTIVE)));

            service.refresh("old", CLIENT);

            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
            assertThat(saved.getAllValues()).extracting(RefreshToken::getSessionId).containsOnly("sess-1");
        }
    }
}
