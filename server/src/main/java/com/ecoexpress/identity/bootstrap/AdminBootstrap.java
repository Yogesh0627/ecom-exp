package com.ecoexpress.identity.bootstrap;

import com.ecoexpress.identity.domain.Role;
import com.ecoexpress.identity.domain.User;
import com.ecoexpress.identity.domain.UserStatus;
import com.ecoexpress.identity.repository.RoleRepository;
import com.ecoexpress.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Creates the first ADMIN account, without which the admin dashboard is unreachable —
 * there is no way to grant the ADMIN role through the API before one admin exists.
 *
 * <p>Deliberately NOT a Flyway migration: a seeded password hash in a migration file is
 * committed to git forever, identical across every environment, and cannot be rotated
 * without another migration.
 *
 * <p>Runs only when {@code ecoexpress.bootstrap.admin-email} is set, and only creates
 * the account if it does not already exist — so a restart never resets a changed
 * password, and production simply omits the property.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ecoexpress.bootstrap", name = "admin-email")
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmailIgnoreCase(props.adminEmail())) {
            log.debug("Admin {} already exists — leaving it alone", props.adminEmail());
            return;
        }

        if (props.adminPassword() == null || props.adminPassword().length() < 12) {
            // Refuse rather than silently create a weak account with every permission.
            throw new IllegalStateException(
                    "ecoexpress.bootstrap.admin-password must be at least 12 characters "
                            + "to create the ADMIN account.");
        }

        Role adminRole = roleRepository.findByNameWithPermissions("ADMIN")
                .orElseThrow(() -> new IllegalStateException(
                        "ADMIN role missing — the V1 seed did not run."));

        User admin = User.builder()
                .email(props.adminEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(props.adminPassword()))
                .fullName(props.adminName())
                .status(UserStatus.ACTIVE)
                // Bootstrap admin is trusted by definition; no verification email exists yet.
                .emailVerifiedAt(Instant.now())
                .build();
        admin.addRole(adminRole);
        userRepository.save(admin);

        log.warn("Created bootstrap ADMIN account {} — change this password before launch.",
                props.adminEmail());
    }
}
