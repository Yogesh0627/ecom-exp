package com.ecoexpress.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * {@link AuditableEntity} plus soft delete (PRD §12).
 *
 * <p>Extending this provides the {@code deleted_at} column and the flag, but a subclass
 * must add {@code @SQLRestriction("deleted_at IS NULL")} to actually filter reads.
 *
 * <p>Do NOT extend this for a table without a {@code deleted_at} column — Hibernate's
 * schema validation will refuse to start the app. Those entities extend
 * {@link AuditableEntity} instead. Two groups of tables are like that:
 * <ul>
 *   <li>{@code oauth_accounts} — a soft-deleted row would trip
 *       UNIQUE(provider, provider_user_id) and permanently block re-linking.</li>
 *   <li>Ledgers ({@code payments}, {@code stock_transactions},
 *       {@code order_status_history}, {@code coupon_redemptions}) — financial history
 *       is never erased; corrections are compensating rows.</li>
 * </ul>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity extends AuditableEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
