package com.ecoexpress.ai.domain;

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

import java.util.UUID;

/**
 * One meal slot in a plan (V7). UNIQUE(meal_plan_id, day_of_week, meal_type): one entry per slot.
 *
 * <p>{@code recipeId} links to a stored recipe when one exists; {@code customTitle} is the
 * free-text fallback when the AI suggests something we do not have as a recipe row. A CHECK
 * requires at least one of the two — an entry must point at something.
 */
@Entity
@Table(name = "meal_plan_entries")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealPlanEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_plan_id", nullable = false)
    private MealPlan mealPlan;

    /** Optional link to a stored recipe. Not an FK relationship here to keep the AI module from
     *  depending on the recipe tables; resolved by id when needed. */
    @Column(name = "recipe_id")
    private UUID recipeId;

    /** 1 = Monday … 7 = Sunday (mpe_day_chk). */
    @Column(name = "day_of_week", nullable = false)
    private Short dayOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false)
    private MealType mealType;

    @Column(name = "servings", nullable = false)
    @Builder.Default
    private Integer servings = 1;

    /** Free-text meal when there is no recipe row — what the AI actually suggested. */
    @Column(name = "custom_title")
    private String customTitle;

    @Column(name = "is_completed", nullable = false)
    @Builder.Default
    private Boolean isCompleted = false;
}
