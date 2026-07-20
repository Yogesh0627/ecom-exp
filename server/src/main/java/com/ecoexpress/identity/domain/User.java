package com.ecoexpress.identity.domain;

import com.ecoexpress.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A user account. Maps to the {@code users} table (V1).
 *
 * <p>Soft-deleted rows are filtered out of every read by {@link SQLRestriction}.
 */
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "email", nullable = false)
    private String email;

    /** Null for users who only ever sign in with Google. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    /** A new email awaiting verification (change-email is verify-before-switch). Null when none pending. */
    @Column(name = "pending_email")
    private String pendingEmail;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * EAGER because every authenticated request needs the user's authorities to build
     * the SecurityContext — lazy would just guarantee an extra query on each one.
     * The set is small and bounded (a handful of roles).
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<OAuthAccount> oauthAccounts = new HashSet<>();

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /** True when this account can only authenticate via an OAuth provider. */
    public boolean isOAuthOnly() {
        return passwordHash == null;
    }

    public void addRole(Role role) {
        roles.add(role);
    }
}
