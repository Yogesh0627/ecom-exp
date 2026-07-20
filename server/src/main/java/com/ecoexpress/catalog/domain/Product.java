package com.ecoexpress.catalog.domain;

import com.ecoexpress.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A product (V2). Price, SKU and barcode live on {@link ProductVariant}, not here:
 * "Organic Turmeric" is a product; the 200g and 500g packs are variants.
 *
 * <p>The table also has a generated {@code search_vector} tsvector column backing
 * full-text search. It is deliberately NOT mapped — Postgres maintains it, and mapping
 * a GENERATED ALWAYS column invites Hibernate to try to write it.
 */
@Entity
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "origin")
    private String origin;

    @Column(name = "is_organic", nullable = false)
    @Builder.Default
    private Boolean isOrganic = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** HSN code for the GST invoice (V8). */
    @Column(name = "hsn_code")
    private String hsnCode;

    /**
     * GST rate (V8). Zero for nil-rated fresh produce, which is most of this catalog;
     * packaged goods attract 5% or more. Copied onto the order line at checkout so a
     * later rate change never rewrites an old invoice.
     */
    @Column(name = "gst_rate_pct", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstRatePct = BigDecimal.ZERO;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("weightGrams ASC")
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    /** The variant shown on a listing card. Exactly one per product (unique index in V2). */
    public ProductVariant defaultVariant() {
        return variants.stream()
                .filter(v -> Boolean.TRUE.equals(v.getIsDefault()))
                .findFirst()
                .orElseGet(() -> variants.isEmpty() ? null : variants.get(0));
    }
}
