package com.ecoexpress.inventory.domain;

import com.ecoexpress.common.domain.BaseEntity;
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
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A received lot of stock, with its own expiry (V3).
 *
 * <p>Organic produce expires, so allocation is FEFO — first-expiry-first-out, not FIFO.
 * Shipping a later-expiring batch while an earlier one sits on the shelf turns stock
 * into waste.
 */
@Entity
@Table(name = "inventory_batches")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryBatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    /** Supplier's lot number. Unique per inventory row — the recall handle. */
    @Column(name = "lot_no", nullable = false)
    private String lotNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "qty_received", nullable = false)
    private Integer qtyReceived;

    @Column(name = "qty_remaining", nullable = false)
    private Integer qtyRemaining;

    /** What we paid per unit. Drives margin, and COGS at sale time. */
    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    /** Null for non-perishables. Nulls sort last in FEFO. */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    public boolean hasStock() {
        return qtyRemaining > 0;
    }
}
