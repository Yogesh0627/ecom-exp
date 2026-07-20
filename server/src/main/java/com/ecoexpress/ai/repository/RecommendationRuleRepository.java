package com.ecoexpress.ai.repository;

import com.ecoexpress.ai.domain.RecommendationRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecommendationRuleRepository extends JpaRepository<RecommendationRule, UUID> {

    /**
     * Active rules triggered by a specific variant, best-weighted first. The suggested variant
     * and its product are fetched so the response can be built without an N+1.
     */
    @EntityGraph(attributePaths = {"suggestedVariant", "suggestedVariant.product"})
    @Query("""
            SELECT r FROM RecommendationRule r
            WHERE r.triggerVariant.id = :variantId AND r.isActive = true
            ORDER BY r.weight DESC
            """)
    List<RecommendationRule> findActiveByTriggerVariant(@Param("variantId") UUID variantId);

    /** Active rules triggered by a category — the fallback when no variant-specific rule exists. */
    @EntityGraph(attributePaths = {"suggestedVariant", "suggestedVariant.product"})
    @Query("""
            SELECT r FROM RecommendationRule r
            WHERE r.triggerCategory.id = :categoryId AND r.isActive = true
            ORDER BY r.weight DESC
            """)
    List<RecommendationRule> findActiveByTriggerCategory(@Param("categoryId") UUID categoryId);
}
