package com.ecoexpress.common.config;

import com.ecoexpress.common.security.AuthenticatedUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Supplies the current user id for {@code @CreatedBy} / {@code @LastModifiedBy}.
 *
 * <p>Returns empty for unauthenticated writes (system jobs, migrations, public signup),
 * which leaves the audit columns null rather than inventing an actor.
 */
@Configuration
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.empty();
            }
            if (auth.getPrincipal() instanceof AuthenticatedUser user) {
                return Optional.of(user.getId());
            }
            return Optional.empty();
        };
    }
}
