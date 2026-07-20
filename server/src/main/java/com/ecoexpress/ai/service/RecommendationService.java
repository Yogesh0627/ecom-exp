package com.ecoexpress.ai.service;

import com.ecoexpress.ai.domain.RecommendationRule;
import com.ecoexpress.ai.repository.RecommendationRuleRepository;
import com.ecoexpress.catalog.domain.Product;
import com.ecoexpress.catalog.domain.ProductStatus;
import com.ecoexpress.catalog.domain.ProductVariant;
import com.ecoexpress.catalog.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Intelligent recommendations (PRD §5.3) — rule-based, no AI call.
 *
 * <p>The PRD is explicit: "Rule-based initially… Future ML recommendation engine." So this reads
 * {@code recommendation_rules} rows, ranks by weight, and filters to what is actually buyable. It
 * needs no Gemini key. When the ML model arrives it scores the same rows, so nothing that calls
 * this service has to change.
 *
 * <p>A variant-specific rule wins over a category rule for the same suggestion. Out-of-stock and
 * unpublished suggestions are dropped — recommending something a customer cannot buy is worse than
 * recommending nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRuleRepository ruleRepository;
    private final ProductVariantRepository variantRepository;

    /** A single recommendation, ready to render. */
    public record Recommendation(
            UUID variantId, String sku, String productName, String productSlug,
            BigDecimal price, String ruleType, String reason, BigDecimal weight) {}

    /**
     * Recommends complements for a variant, ranked by rule weight.
     *
     * <p>Deliberately inventory-free: it applies catalog rules only (active product, active
     * variant, not the trigger's own product). Filtering by live stock is the caller's job — that
     * keeps this service from depending on the inventory module and preserves the boundary.
     */
    @Transactional(readOnly = true)
    public List<Recommendation> forVariant(UUID variantId, int limit) {
        ProductVariant trigger = variantRepository.findById(variantId).orElse(null);
        if (trigger == null) {
            return List.of();
        }

        // De-dupe by suggested variant, keeping the highest-weighted rule. Variant rules are
        // loaded first so they win over category rules for the same suggestion.
        Map<UUID, RecommendationRule> best = new LinkedHashMap<>();
        for (RecommendationRule rule : ruleRepository.findActiveByTriggerVariant(variantId)) {
            best.putIfAbsent(rule.getSuggestedVariant().getId(), rule);
        }
        UUID categoryId = trigger.getProduct().getCategory().getId();
        for (RecommendationRule rule : ruleRepository.findActiveByTriggerCategory(categoryId)) {
            best.putIfAbsent(rule.getSuggestedVariant().getId(), rule);
        }

        List<Recommendation> out = new ArrayList<>();
        for (RecommendationRule rule : best.values()) {
            ProductVariant suggested = rule.getSuggestedVariant();
            Product product = suggested.getProduct();

            // Never recommend the trigger's own product, an unpublished product, or a
            // deactivated variant.
            if (product.getId().equals(trigger.getProduct().getId())) {
                continue;
            }
            if (product.getStatus() != ProductStatus.ACTIVE
                    || !Boolean.TRUE.equals(suggested.getIsActive())) {
                continue;
            }

            out.add(new Recommendation(
                    suggested.getId(), suggested.getSku(), product.getName(), product.getSlug(),
                    suggested.getPrice(), rule.getRuleType().name(), rule.getReason(), rule.getWeight()));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }
}
