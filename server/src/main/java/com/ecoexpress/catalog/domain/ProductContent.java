package com.ecoexpress.catalog.domain;

import com.ecoexpress.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * Rich, AI-assisted marketing/health content for a product (V11) — one row per product.
 *
 * <p>Content is AI-DRAFTED and human-APPROVED: {@link ProductContentStatus#DRAFT} is never shown to
 * shoppers, only {@link ProductContentStatus#PUBLISHED}. Sections are prose (the storefront renders
 * newline-separated lines as bullets). Health claims are stored cautiously — see {@link #nutrientSupport},
 * the compliance-safe framing of the "disease prevention" ask.
 */
@Entity
@Table(name = "product_content")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductContent extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** A richer description than {@link Product#getDescription()}. */
    @Column(name = "overview")
    private String overview;

    /** Why this product / what's good about it. */
    @Column(name = "advantages")
    private String advantages;

    /** Nutrition-backed benefits, cautiously framed (no cure/prevention claims). */
    @Column(name = "health_benefits")
    private String healthBenefits;

    /** "Nutrients that support…" — the compliance-safe framing of disease-prevention content. */
    @Column(name = "nutrient_support")
    private String nutrientSupport;

    /** Why choose organic / this over the alternatives. */
    @Column(name = "why_choose")
    private String whyChoose;

    /** How to keep it fresh. */
    @Column(name = "storage_tips")
    private String storageTips;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ProductContentStatus status = ProductContentStatus.DRAFT;

    @Column(name = "generated_by_ai", nullable = false)
    @Builder.Default
    private Boolean generatedByAi = false;

    @Column(name = "ai_model")
    private String aiModel;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public boolean isPublished() {
        return status == ProductContentStatus.PUBLISHED;
    }
}
