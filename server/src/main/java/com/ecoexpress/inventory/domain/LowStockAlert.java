package com.ecoexpress.inventory.domain;

import com.ecoexpress.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A low-stock alert (V3, PRD §9).
 *
 * <p>Extends {@link AuditableEntity} (no soft delete): the table has no {@code deleted_at}.
 * A partial unique index guarantees at most one OPEN alert per inventory row — otherwise a
 * product hovering at its reorder point would fire an alert on every stock movement and
 * bury the real ones.
 */
@Entity
@Table(name = "low_stock_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LowStockAlert extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @Column(name = "qty_at_trigger", nullable = false)
    private Integer qtyAtTrigger;

    @Column(name = "reorder_point", nullable = false)
    private Integer reorderPoint;

    @Column(name = "triggered_at", nullable = false)
    @Builder.Default
    private Instant triggeredAt = Instant.now();

    /** Null while the alert is open; set when stock recovers above the reorder point. */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    public boolean isOpen() {
        return resolvedAt == null;
    }
}
