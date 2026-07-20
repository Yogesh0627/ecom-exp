package com.ecoexpress.common.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated principal placed in the SecurityContext.
 *
 * <p>Carries the user id so auditing and ownership checks never re-query by email.
 * Authorities are flattened permission strings (RBAC per PRD §13), not role names —
 * checks read {@code hasAuthority("product:write")} rather than {@code hasRole("ADMIN")},
 * so permissions can be regrouped into roles without touching call sites.
 */
@Getter
@RequiredArgsConstructor
public class AuthenticatedUser implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final Set<GrantedAuthority> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Email is the login identifier; there is no separate username. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
