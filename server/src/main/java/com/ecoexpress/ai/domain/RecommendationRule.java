package com.ecoexpress.ai.domain;

import com.ecoexpress.catalog.domain.Category;
import com.ecoexpress.catalog.domain.ProductVariant;
import com.ecoexpress.common.domain.BaseEntity;
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

/**
 * An intelligent-recommendation rule (V7, PRD §5.3).
 *
 * <p>The PRD's own example is {@code Paneer → Peas, Capsicum, Cream}. Recommendations are
 * <b>data, not code</b>: as rows, ops can edit pairings without a deploy, and the eventual ML
 * model can score the same rows rather than replacing the mechanism. A rule triggers on exactly
 * one thing — a specific variant or a whole category, never both, never neither (a CHECK enforces
 * it) — and a rule cannot recommend a product to itself.
 */
@Entity
@Table(name = "recommendation_rules")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationRule extends BaseEntity {

    /** Trigger: a specific variant. Mutually exclusive with {@link #triggerCategory}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trigger_variant_id")
    private ProductVariant triggerVariant;

    /** Trigger: any product in this category. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trigger_category_id")
    private Category triggerCategory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suggested_variant_id", nullable = false)
    private ProductVariant suggestedVariant;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    @Builder.Default
    private RecommendationType ruleType = RecommendationType.COMPLEMENT;

    /** Ranking weight when several rules fire at once. Higher shows first. */
    @Column(name = "weight", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal weight = BigDecimal.ONE;

    @Column(name = "reason")
    private String reason;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
