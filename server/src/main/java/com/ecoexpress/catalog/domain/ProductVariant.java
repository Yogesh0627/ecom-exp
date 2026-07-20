package com.ecoexpress.catalog.domain;

import com.ecoexpress.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * A sellable unit: a specific pack size of a product (V2).
 *
 * <p>Money is BigDecimal — NUMERIC(12,2) in the DB. Never double: 0.1 + 0.2 != 0.3 in
 * binary floating point, and an order total that is off by a paisa is a real complaint.
 */
@Entity
@Table(name = "product_variants")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "weight_grams", nullable = false, precision = 10, scale = 2)
    private BigDecimal weightGrams;

    /** Printed maximum retail price. We may never charge above it (variants_price_lte_mrp). */
    @Column(name = "mrp", nullable = false, precision = 12, scale = 2)
    private BigDecimal mrp;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /**
     * The column is CHAR(3) (Postgres bpchar). Hibernate maps String to varchar by
     * default, which fails schema validation — hence the explicit JDBC type.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @OneToOne(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true,
              fetch = FetchType.LAZY)
    private NutritionFacts nutritionFacts;

    /**
     * Batch-loaded, not join-fetched: this is the second bag on the product-detail path
     * and Hibernate cannot fetch two Lists in one query. @BatchSize collapses what would
     * be one query per variant into a single IN query for all of them.
     */
    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    /**
     * Derived, never stored: a persisted copy drifts the moment price or mrp changes,
     * and then the storefront advertises a discount that is not real.
     */
    public BigDecimal discountPercent() {
        if (mrp == null || price == null || mrp.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return mrp.subtract(price)
                .multiply(BigDecimal.valueOf(100))
                .divide(mrp, 2, RoundingMode.HALF_UP);
    }

    public ProductImage primaryImage() {
        return images.stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsPrimary()))
                .findFirst()
                .orElseGet(() -> images.isEmpty() ? null : images.get(0));
    }
}
