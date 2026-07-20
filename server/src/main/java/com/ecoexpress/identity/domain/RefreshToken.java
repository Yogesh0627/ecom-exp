package com.ecoexpress.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A refresh token, stored as a SHA-256 hash.
 *
 * <p>Deliberately does NOT extend BaseEntity: the refresh_tokens table has no
 * created_at/updated_at/version columns (it uses issued_at), and no soft delete —
 * revocation is {@code revoked_at}, and expired rows are hard-deleted by a cleanup job.
 *
 * <p><b>Rotation and theft detection.</b> Every use mints a replacement and sets
 * {@link #replacedBy} on the old row. A token that is presented while it already has a
 * replacement has been replayed — meaning it leaked — so the whole chain for that user
 * is revoked. This is why the row survives rotation instead of being deleted.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 of the raw token. The raw value is returned to the client once and never stored. */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    @Builder.Default
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Set when this token is rotated. Non-null + presented again == replay. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    private RefreshToken replacedBy;

    @Column(name = "user_agent")
    private String userAgent;

    /**
     * The column is PostgreSQL {@code inet}. Without the explicit JDBC type code
     * Hibernate binds a String as varchar and Postgres refuses the implicit cast:
     * "column ip_address is of type inet but expression is of type character varying".
     */
    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private String ipAddress;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isRotated() {
        return replacedBy != null;
    }

    /** Usable exactly once: not expired, not revoked, not already rotated. */
    public boolean isUsable() {
        return !isExpired() && !isRevoked() && !isRotated();
    }
}
