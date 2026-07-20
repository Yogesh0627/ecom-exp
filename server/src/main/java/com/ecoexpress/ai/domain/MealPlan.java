package com.ecoexpress.ai.domain;

import com.ecoexpress.common.domain.BaseEntity;
import com.ecoexpress.identity.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A weekly meal plan (V7, PRD §5.4). One plan per user per week (partial unique index).
 *
 * <p>{@code weekStart} must be a Monday — a CHECK enforces it, so "week of" means the same thing
 * for every row and the planner cannot double-book days.
 */
@Entity
@Table(name = "meal_plans")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealPlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal", nullable = false)
    private MealGoal goal;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MealPlanStatus status = MealPlanStatus.DRAFT;

    @Column(name = "target_calories_per_day")
    private Integer targetCaloriesPerDay;

    @Column(name = "notes")
    private String notes;

    @Column(name = "generated_by", nullable = false)
    @Builder.Default
    private String generatedBy = "AI";

    @OneToMany(mappedBy = "mealPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @Builder.Default
    private List<MealPlanEntry> entries = new ArrayList<>();
}
