package com.itilms.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.NotificationRequestedEvent;
import com.itilms.common.event.UserCreatedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.identity.config.IdentityProperties;
import com.itilms.identity.dto.response.AuthResponse;
import com.itilms.identity.entity.LoginOtp;
import com.itilms.identity.entity.User;
import com.itilms.identity.entity.UserRole;
import com.itilms.identity.entity.UserStatus;
import com.itilms.identity.repository.LoginOtpRepository;
import com.itilms.identity.repository.UserRepository;
import com.itilms.identity.security.JwtIssuer;
import com.itilms.identity.service.AuthService.ClientMetadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The two ways to sign in without typing a password: "Continue with Google" and a code sent on WhatsApp.
 *
 * <p>Both end the same way as a password sign-in, through {@link AuthService#signInVerified}: the account status,
 * the lock-out and the one-active-login rule all apply. A person with no account gets a student account only when
 * online registration is open, exactly as for the register form.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final LoginOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final EventPublisher events;
    private final IdentityProperties properties;
    private final AuthService authService;
    private final GoogleTokenVerifier google;
    private final WhatsAppSender whatsApp;

    /** What the sign-in page needs to know before anyone has a token. */
    public Config config() {
        return new Config(google.clientId(), whatsApp.enabled());
    }

    public record Config(String googleClientId, boolean whatsappEnabled) {
    }

    // -----------------------------------------------------------------
    // Google
    // -----------------------------------------------------------------

    @Transactional(noRollbackFor = {BadCredentialsException.class, ForbiddenOperationException.class})
    public AuthResponse googleLogin(String idToken, ClientMetadata metadata) {
        GoogleTokenVerifier.Profile profile = google.verify(idToken);

        User user = userRepository.findByEmailIgnoreCase(profile.email()).orElse(null);
        if (user == null) {
            requireOpenRegistration("No account uses this Google address. Please contact the institute to enrol.");
            user = createStudent(firstNameOf(profile), lastNameOf(profile), profile.email(), null);
            notifyWelcome(user);
        }
        return authService.signInVerified(user, metadata);
    }

    private static String firstNameOf(GoogleTokenVerifier.Profile p) {
        if (notBlank(p.givenName())) {
            return cut(p.givenName().trim());
        }
        if (notBlank(p.name())) {
            return cut(p.name().trim().split("\\s+")[0]);
        }
        return "Student";
    }

    private static String lastNameOf(GoogleTokenVerifier.Profile p) {
        if (notBlank(p.familyName())) {
            return cut(p.familyName().trim());
        }
        if (notBlank(p.name()) && p.name().trim().contains(" ")) {
            String name = p.name().trim();
            return cut(name.substring(name.indexOf(' ') + 1).trim());
        }
        return "-";
    }

    // -----------------------------------------------------------------
    // WhatsApp code
    // -----------------------------------------------------------------

    /**
     * Sends a code when the number may sign in. Returns normally in every other case (unknown number, closed
     * registration, too many requests, a code sent a moment ago), so the response never tells a stranger which
     * numbers are registered.
     */
    @Transactional
    public void requestCode(String rawPhone, ClientMetadata metadata) {
        IdentityProperties.WhatsApp settings = properties.getWhatsapp();
        if (!settings.isEnabled()) {
            throw new BusinessRuleException("WHATSAPP_DISABLED", "Sign-in with a WhatsApp code is not switched on.");
        }
        String phone = canonical(rawPhone);

        List<User> matches = userRepository.findAllByPhoneEndingWith(last10(phone));
        boolean mayReceive;
        if (matches.size() > 1) {
            log.warn("Several accounts share a phone number; no sign-in code sent");
            mayReceive = false;
        } else if (matches.isEmpty()) {
            mayReceive = properties.getSecurity().isSelfRegistrationEnabled();
        } else {
            mayReceive = matches.get(0).getStatus().canSignIn();
        }
        if (!mayReceive) {
            return;
        }

        Instant now = Instant.now();
        boolean tooSoon = otpRepository.findFirstByPhoneDigitsOrderByCreatedAtDesc(phone)
                .map(o -> o.getCreatedAt().isAfter(now.minus(settings.getResendCooldown())))
                .orElse(false);
        boolean tooMany = otpRepository.countRecent(phone, now.minus(Duration.ofHours(1)))
                >= settings.getMaxRequestsPerHour();
        if (tooSoon || tooMany) {
            log.info("Sign-in code request held back (cooldown or hourly limit)");
            return;
        }

        otpRepository.invalidateAllForPhone(phone, now);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otpRepository.save(LoginOtp.builder()
                .phoneDigits(phone)
                .codeHash(codeHash(phone, code))
                .expiresAt(now.plus(settings.getCodeTtl()))
                .requestedIp(metadata.ipAddress())
                .build());

        whatsApp.sendCode(phone, code);
        log.info("WhatsApp sign-in code issued");
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, ForbiddenOperationException.class})
    public AuthResponse verifyCode(String rawPhone, String code, ClientMetadata metadata) {
        IdentityProperties.WhatsApp settings = properties.getWhatsapp();
        if (!settings.isEnabled()) {
            throw new BusinessRuleException("WHATSAPP_DISABLED", "Sign-in with a WhatsApp code is not switched on.");
        }
        String phone = canonical(rawPhone);

        LoginOtp otp = otpRepository.findFirstByPhoneDigitsAndUsedAtIsNullOrderByCreatedAtDesc(phone)
                .filter(LoginOtp::isUsable)
                .orElseThrow(() -> new BadCredentialsException("The code is wrong or has expired"));

        if (otp.getAttempts() >= settings.getMaxAttempts()) {
            throw new BadCredentialsException("Too many wrong codes. Please ask for a new one.");
        }
        boolean match = MessageDigest.isEqual(
                otp.getCodeHash().getBytes(StandardCharsets.UTF_8),
                codeHash(phone, code == null ? "" : code.trim()).getBytes(StandardCharsets.UTF_8));
        if (!match) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            throw new BadCredentialsException("The code is wrong or has expired");
        }
        otp.setUsedAt(Instant.now());
        otpRepository.save(otp);

        List<User> matches = userRepository.findAllByPhoneEndingWith(last10(phone));
        if (matches.size() > 1) {
            throw new BadCredentialsException("The code is wrong or has expired");
        }
        User user;
        if (matches.isEmpty()) {
            requireOpenRegistration("No account uses this number. Please contact the institute to enrol.");
            user = createStudent("Student", last4(phone), phone + "@phone.invalid", phone);
        } else {
            user = matches.get(0);
        }
        return authService.signInVerified(user, metadata);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private void requireOpenRegistration(String message) {
        if (!properties.getSecurity().isSelfRegistrationEnabled()) {
            throw new ForbiddenOperationException(message);
        }
    }

    /** A student account made on the first sign-in; mirrors the register form's account. */
    private User createStudent(String firstName, String lastName, String email, String phone) {
        User user = userRepository.save(User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(phone)
                // Nobody knows this password; the person signs in by Google or WhatsApp, or resets it by email.
                .passwordHash(passwordEncoder.encode(UUID.randomUUID() + "." + UUID.randomUUID()))
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build());
        log.info("Sign-in without a password created student account {}", user.getId());

        events.publishAfterCommit(KafkaTopics.USER_CREATED,
                UserCreatedEvent.selfRegistered(user.getId(), user.getEmail(), user.getPhone(),
                        user.fullName(), user.getRole().name()));
        return user;
    }

    private void notifyWelcome(User user) {
        events.publishAfterCommit(KafkaTopics.NOTIFICATION_REQUESTED,
                NotificationRequestedEvent.toUsersWithEmail(
                        List.of(user.getId()), "ACCOUNT_CREATED",
                        "Welcome to the institute",
                        "Your account is ready. Browse our courses and speak to a counselor to enrol.",
                        "/student/dashboard"));
    }

    /** Country code plus number, digits only. A 10-digit number gets the configured country code. */
    String canonical(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        String cc = properties.getWhatsapp().getDefaultCountryCode();
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() == 10) {
            digits = cc + digits;
        }
        if (digits.length() < 11 || digits.length() > 15) {
            throw new BusinessRuleException("Enter a valid mobile number with country code");
        }
        return digits;
    }

    private static String last10(String digits) {
        return digits.substring(digits.length() - 10);
    }

    private static String last4(String digits) {
        return digits.substring(digits.length() - 4);
    }

    private String codeHash(String phone, String code) {
        return jwtIssuer.hash(phone + ":" + code);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String cut(String value) {
        return value.length() > 80 ? value.substring(0, 80) : value;
    }
}
