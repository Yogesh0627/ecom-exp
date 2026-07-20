package com.ecoexpress.ai.domain;

import com.ecoexpress.catalog.domain.ProductVariant;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A household pantry item (V7, PRD §5.5).
 *
 * <p>The pantry's whole job is "consume what you have before suggesting a purchase". So the
 * recommender and meal planner consult it: if you already have atta, they should not sell you
 * more. {@code variantId} is nullable — you can track "half a bag of rice" even if it does not
 * map to a specific SKU we sell.
 */
@Entity
@Table(name = "pantry_items")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PantryItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "ingredient_name", nullable = false)
    private String ingredientName;

    /** Links to a catalog variant when known; null for free-text pantry entries. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(name = "qty", nullable = false, precision = 10, scale = 3)
    private BigDecimal qty;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false)
    @Builder.Default
    private PantryUnit unit = PantryUnit.PIECE;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    @Builder.Default
    private PantrySource source = PantrySource.MANUAL;

    /** The delivered order this item came from, if auto-added on delivery. */
    @Column(name = "source_order_id")
    private UUID sourceOrderId;

    /** Set when the user marks it used up; consumed items are excluded from "what I have". */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "expiry_notified_at")
    private Instant expiryNotifiedAt;

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpiringWithin(int days) {
        return expiryDate != null && !expiryDate.isAfter(LocalDate.now().plusDays(days));
    }
}
