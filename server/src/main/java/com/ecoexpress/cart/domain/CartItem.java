package com.ecoexpress.cart.domain;

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

import java.math.BigDecimal;

/**
 * A line in a cart (V4).
 *
 * <p>UNIQUE(cart_id, variant_id): adding the same variant twice increments the quantity,
 * it never creates a second line.
 */
@Entity
@Table(name = "cart_items")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    /**
     * The price when this line was added.
     *
     * <p>Not used for charging — checkout re-reads the live price. It exists so we can
     * detect that the price moved while the item sat in the cart and TELL the customer,
     * rather than silently charging a different number than the one they saw.
     */
    @Column(name = "unit_price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceSnapshot;

    /** Live line total, at the current catalog price. */
    public BigDecimal lineTotal() {
        return variant.getPrice().multiply(BigDecimal.valueOf(qty));
    }

    /** True when the catalog price has moved since this line was added. */
    public boolean priceChanged() {
        return variant.getPrice().compareTo(unitPriceSnapshot) != 0;
    }
}
