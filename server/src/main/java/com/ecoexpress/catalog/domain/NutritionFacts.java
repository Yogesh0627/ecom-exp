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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The 12 nutrients per 100g (PRD §8), backing Smart Cart Nutrition (§5.2).
 *
 * <p><b>Every nutrient is nullable, and that is load-bearing.</b> NULL means "not
 * measured"; zero means "measured, contains none". Collapsing the two would let a
 * health score treat an unmeasured iron value as zero iron and quietly report a
 * confident, wrong number. Anything consuming these must handle NULL explicitly —
 * see {@link #isComplete()}.
 */
@Entity
@Table(name = "nutrition_facts")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NutritionFacts extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    /** Reference weight the values below describe. Always 100g in practice. */
    @Column(name = "basis_grams", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal basisGrams = BigDecimal.valueOf(100);

    @Column(name = "calories_kcal", precision = 9, scale = 3)
    private BigDecimal caloriesKcal;

    @Column(name = "protein_g", precision = 9, scale = 3)
    private BigDecimal proteinG;

    @Column(name = "fat_g", precision = 9, scale = 3)
    private BigDecimal fatG;

    @Column(name = "carbohydrates_g", precision = 9, scale = 3)
    private BigDecimal carbohydratesG;

    @Column(name = "fiber_g", precision = 9, scale = 3)
    private BigDecimal fiberG;

    @Column(name = "sugar_g", precision = 9, scale = 3)
    private BigDecimal sugarG;

    @Column(name = "iron_mg", precision = 9, scale = 3)
    private BigDecimal ironMg;

    @Column(name = "vitamin_a_mcg", precision = 9, scale = 3)
    private BigDecimal vitaminAMcg;

    @Column(name = "vitamin_c_mg", precision = 9, scale = 3)
    private BigDecimal vitaminCMg;

    @Column(name = "vitamin_d_mcg", precision = 9, scale = 3)
    private BigDecimal vitaminDMcg;

    @Column(name = "potassium_mg", precision = 9, scale = 3)
    private BigDecimal potassiumMg;

    @Column(name = "sodium_mg", precision = 9, scale = 3)
    private BigDecimal sodiumMg;

    /** Provenance: an IFCT figure and a hand-typed label are not equally trustworthy. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    @Builder.Default
    private NutritionSource source = NutritionSource.MANUAL;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /**
     * True only when the macronutrients are all present. The cart's nutrition summary
     * uses this to decide whether to present a total as a fact or flag it as partial.
     */
    public boolean isComplete() {
        return caloriesKcal != null && proteinG != null && fatG != null
                && carbohydratesG != null;
    }
}
