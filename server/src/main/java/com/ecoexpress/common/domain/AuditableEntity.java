package com.ecoexpress.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Identity, audit fields and optimistic locking (PRD §12) — WITHOUT soft delete.
 *
 * <p>Extend this for tables that genuinely have no {@code deleted_at} column:
 * {@code oauth_accounts} (a lingering soft-deleted row would trip
 * UNIQUE(provider, provider_user_id) and block re-linking), and the ledger tables
 * whose history must never be erased.
 *
 * <p>For everything else extend {@link BaseEntity}, which adds soft delete on top.
 *
 * <p>UUID keys: non-enumerable in public URLs, and assignable before insert so an object
 * graph can be wired up in one flush. Hibernate generates them client-side; the DDL
 * default gen_random_uuid() only covers rows inserted by raw SQL. Postgres 17 has no
 * uuidv7(), so these are random v4 — see docs/ERD.md on index locality.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    /** Optimistic locking. Critical for inventory: two concurrent decrements must not both win. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Compared by identity, not by JPA proxy class, so a lazy proxy and its loaded
     * instance are equal. A null id means "not yet persisted" and is never equal to
     * anything — otherwise two new entities would collide in a HashSet.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuditableEntity other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant hash is intentional: the id is null before persist and set after, so
        // an id-derived hash would change bucket mid-session and corrupt HashSet membership.
        return getClass().hashCode();
    }
}
