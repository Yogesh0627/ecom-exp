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
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Seeds a pre-verified demo CUSTOMER for the public showcase, so the storefront's one-click
 * "sign in as User" button works immediately — already email-verified (no verification banner)
 * and safe to hand to recruiters.
 *
 * <p>Runs only when {@code ecoexpress.bootstrap.demo.enabled=true}. Creates the account if absent;
 * if it already exists but is unverified (e.g. a prior self-service signup), it flips it to
 * verified so the demo experience is always clean. Ordered after {@link AdminBootstrap} so the
 * ADMIN seed logs first.
 */
@Slf4j
@Component
@Order(20)
@ConditionalOnProperty(prefix = "ecoexpress.bootstrap.demo", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoCustomerBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoAccountProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (props.email() == null || props.email().isBlank()
                || props.password() == null || props.password().isBlank()) {
            log.warn("Demo customer enabled but email/password not set — skipping.");
            return;
        }

        var existing = userRepository.findByEmailWithAuthorities(props.email());
        if (existing.isPresent()) {
            User user = existing.get();
            if (!user.isEmailVerified()) {
                user.setEmailVerifiedAt(Instant.now());
                userRepository.save(user);
                log.info("Demo customer {} already existed — marked email-verified.", props.email());
            }
            return;
        }

        Role customerRole = roleRepository.findByNameWithPermissions("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException(
                        "CUSTOMER role missing — the V1 seed did not run."));

        User demo = User.builder()
                .email(props.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(props.password()))
                .fullName(props.name())
                .status(UserStatus.ACTIVE)
                // Pre-verified: the demo account should never see the verify-email banner.
                .emailVerifiedAt(Instant.now())
                .build();
        demo.addRole(customerRole);
        userRepository.save(demo);

        log.info("Created pre-verified demo CUSTOMER account {}", props.email());
    }
}
