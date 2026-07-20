package com.ecoexpress.ai.repository;

import com.ecoexpress.ai.domain.MealPlan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MealPlanRepository extends JpaRepository<MealPlan, UUID> {

    @EntityGraph(attributePaths = {"entries"})
    @Query("SELECT m FROM MealPlan m WHERE m.id = :id")
    Optional<MealPlan> findByIdWithEntries(@Param("id") UUID id);
}
