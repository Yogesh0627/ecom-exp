package com.ecoexpress.promotion.domain;

import com.ecoexpress.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * A discount coupon (V5).
 *
 * <p>Codes are case-insensitive ("SAVE20" == "save20"; a unique index on upper(code) enforces
 * it). A CHECK stops a PERCENT coupon exceeding 100% — a "150% off" coupon would pay the
 * customer to shop.
 *
 * <p>{@code timesUsed} is a cached counter for a fast pre-check; the {@code coupon_redemptions}
 * ledger is the source of truth for how many times a coupon was actually applied.
 */
@Entity
@Table(name = "coupons")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CouponType type;

    /** PERCENT: a percentage (≤100). FLAT: a rupee amount. FREE_SHIPPING: ignored. */
    @Column(name = "value", nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    /** Caps a PERCENT coupon in absolute terms: "20% off, up to ₹200". Null = uncapped. */
    @Column(name = "max_discount", precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "min_cart_value", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal minCartValue = BigDecimal.ZERO;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    /** Total uses across all users. Null = unlimited. */
    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "max_uses_per_user", nullable = false)
    @Builder.Default
    private Integer maxUsesPerUser = 1;

    @Column(name = "times_used", nullable = false)
    @Builder.Default
    private Integer timesUsed = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** Restricts the coupon to a customer's first order. */
    @Column(name = "first_order_only", nullable = false)
    @Builder.Default
    private Boolean firstOrderOnly = false;

    /** True when the coupon is active and within its validity window right now. */
    public boolean isLiveAt(Instant when) {
        return Boolean.TRUE.equals(isActive)
                && !when.isBefore(validFrom) && when.isBefore(validUntil);
    }

    /**
     * The discount this coupon yields for a given cart subtotal.
     *
     * <p>Clamped to the subtotal (a discount can never exceed the order value) and, for
     * PERCENT, to {@code maxDiscount}. FREE_SHIPPING returns zero here — shipping is handled by
     * the pricing engine, not as a line discount.
     */
    public BigDecimal discountFor(BigDecimal subtotal) {
        BigDecimal discount = switch (type) {
            case FLAT -> value;
            case PERCENT -> {
                BigDecimal raw = subtotal.multiply(value)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                yield maxDiscount != null && raw.compareTo(maxDiscount) > 0 ? maxDiscount : raw;
            }
            case FREE_SHIPPING -> BigDecimal.ZERO;
        };
        return discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isFreeShipping() {
        return type == CouponType.FREE_SHIPPING;
    }
}
