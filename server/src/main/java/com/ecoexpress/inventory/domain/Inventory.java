package com.ecoexpress.inventory.domain;

import com.ecoexpress.catalog.domain.ProductVariant;
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

/**
 * Stock position for one variant in one warehouse (V3).
 *
 * <p><b>This row is a cache, not the truth.</b> {@code stock_transactions} is the
 * append-only ledger and the source of record; {@code qtyOnHand} is a rollup of it kept
 * here so reads do not have to sum the ledger. The {@code inventory_ledger_drift} view
 * exists to prove the two agree.
 *
 * <p>Every mutation goes through InventoryService, which writes a ledger row in the same
 * transaction. Updating this entity directly and skipping the ledger silently breaks the
 * PRD §2 "inventory accuracy" guarantee.
 */
@Entity
@Table(name = "inventory")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    /** Physical units in the building. */
    @Column(name = "qty_on_hand", nullable = false)
    @Builder.Default
    private Integer qtyOnHand = 0;

    /**
     * Units promised to carts/orders that have not shipped yet. They are still physically
     * present — so they count in qtyOnHand — but must not be sold twice.
     */
    @Column(name = "qty_reserved", nullable = false)
    @Builder.Default
    private Integer qtyReserved = 0;

    @Column(name = "reorder_point", nullable = false)
    @Builder.Default
    private Integer reorderPoint = 0;

    @Column(name = "reorder_qty", nullable = false)
    @Builder.Default
    private Integer reorderQty = 0;

    /** What a customer can actually buy right now. */
    public int available() {
        return qtyOnHand - qtyReserved;
    }

    public boolean isBelowReorderPoint() {
        return qtyOnHand <= reorderPoint;
    }
}
