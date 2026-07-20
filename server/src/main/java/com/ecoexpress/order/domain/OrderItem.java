package com.ecoexpress.order.domain;

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
 * One line of an order (V4).
 *
 * <p>The variant FK is ON DELETE RESTRICT and every display field is snapshot. The FK
 * exists for reporting ("how many units of SKU X have we sold"); the snapshot is what
 * the invoice actually renders. Reading the live variant to print an old invoice would
 * show today's name and price for a purchase made months ago.
 */
@Entity
@Table(name = "order_items")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** For reporting only — never for rendering. See the snapshot fields below. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Column(name = "variant_name_snapshot", nullable = false)
    private String variantNameSnapshot;

    @Column(name = "sku_snapshot", nullable = false)
    private String skuSnapshot;

    @Column(name = "image_url_snapshot")
    private String imageUrlSnapshot;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    /** What we actually charged, per unit. */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** GST rate copied from the product at checkout, so a rate change cannot rewrite this. */
    @Column(name = "tax_rate_pct", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRatePct = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** Must equal (unit_price * qty) - discount + tax — asserted by order_items_line_chk. */
    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "hsn_code")
    private String hsnCode;
}
