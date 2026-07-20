package com.ecoexpress.inventory.domain;

import com.ecoexpress.common.domain.BaseEntity;
import com.ecoexpress.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.Instant;

/**
 * A reason-coded stock correction requiring approval (V3).
 *
 * <p>Shrinkage is where money quietly leaks in a grocery business. Every manual correction
 * is reason-coded ({@code DAMAGE/EXPIRY/COUNT_CORRECTION/THEFT/SAMPLE/OTHER}) so "how much
 * did we lose to spoilage last month" is a query, not a guess — and an applied adjustment
 * must name who approved it ({@code adjustment_approval_chk}: approved_at and approved_by
 * move together or not at all).
 */
@Entity
@Table(name = "stock_adjustments")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private InventoryBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private AdjustmentReason reason;

    /** Signed. Never zero (adjustment_delta_chk). */
    @Column(name = "qty_delta", nullable = false)
    private Integer qtyDelta;

    @Column(name = "note")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    public boolean isApproved() {
        return approvedAt != null;
    }
}
