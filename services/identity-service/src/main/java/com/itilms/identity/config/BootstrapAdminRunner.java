package com.itilms.identity.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.UserCreatedEvent;
import com.itilms.identity.entity.User;
import com.itilms.identity.entity.UserRole;
import com.itilms.identity.entity.UserStatus;
import com.itilms.identity.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates the first administrator on an empty database.
 *
 * <p>A fresh install has no accounts, so there is nobody who can sign in to
 * create the first one. This closes that loop exactly once.
 *
 * <p>Two deliberate choices:
 * <ul>
 *   <li><b>No default password.</b> If {@code BOOTSTRAP_ADMIN_PASSWORD} is not
 *       set, startup fails with an explanation rather than seeding something
 *       like "admin123". A default admin password is public knowledge the
 *       moment the project is shared, and it always survives into production.</li>
 *   <li><b>Runs only when no administrator exists.</b> Not "if this email is
 *       missing" — otherwise deleting the seeded account would silently
 *       recreate it on the next restart, and an institute that deliberately
 *       renamed its admin would find the old one back.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;
    private final EventPublisher events;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var bootstrap = properties.getBootstrap();
        if (!bootstrap.isEnabled()) {
            log.debug("Admin bootstrap is disabled");
            return;
        }

        long existingAdmins = userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
        if (existingAdmins > 0) {
            log.debug("{} active administrator(s) already exist; skipping bootstrap", existingAdmins);
            return;
        }

        if (bootstrap.getAdminPassword() == null || bootstrap.getAdminPassword().isBlank()) {
            throw new IllegalStateException("""
                    No administrator account exists and BOOTSTRAP_ADMIN_PASSWORD is not set.
                    Set it in the environment so the first administrator can be created:
                      BOOTSTRAP_ADMIN_PASSWORD=<a strong password>
                    Alternatively set BOOTSTRAP_ENABLED=false if you will create the account another way.""");
        }

        User admin = userRepository.save(User.builder()
                .firstName(bootstrap.getAdminFirstName())
                .lastName(bootstrap.getAdminLastName())
                .email(bootstrap.getAdminEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(bootstrap.getAdminPassword()))
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                // Prompted to change it, but not forced: locking the only
                // account out of its own reset flow would be worse.
                .mustChangePassword(false)
                .build());

        // Services that keep a directory of users (notification-service sends
        // role-wide alerts from it) learn about the first administrator here.
        events.publishAfterCommit(KafkaTopics.USER_CREATED, UserCreatedEvent.of(admin.getId(),
                admin.getEmail(), admin.getPhone(), admin.fullName(), admin.getRole().name()));

        log.warn("Bootstrapped the first administrator: {} (id={}). "
                        + "Sign in and change this password now.",
                admin.getEmail(), admin.getId());
    }
}
