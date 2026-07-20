package com.ecoexpress.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CartDtos {

    private CartDtos() {}

    // ---------- requests ----------

    public record AddItemRequest(
            @NotNull UUID variantId,
            @Min(value = 1, message = "quantity must be at least 1") int qty) {}

    public record UpdateItemRequest(
            @Min(value = 0, message = "quantity cannot be negative") int qty) {}

    // ---------- responses ----------

    public record CartItemResponse(
            UUID id,
            UUID variantId,
            String sku,
            String productName,
            String variantName,
            String productSlug,
            String imageUrl,
            int qty,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            BigDecimal weightGrams,
            /** True when the catalog price moved since this line was added. */
            boolean priceChanged,
            BigDecimal priceWhenAdded,
            /** Units currently purchasable. Lets the UI flag a line that can no longer ship. */
            int availableStock) {}

    public record CartResponse(
            UUID id,
            List<CartItemResponse> items,
            int totalUnits,
            BigDecimal subtotal,
            String currency,
            /** True if ANY line's price moved — the UI must surface this before checkout. */
            boolean hasPriceChanges,
            /** Lines whose requested quantity exceeds available stock. */
            List<UUID> unavailableVariantIds,
            NutritionSummaryResponse nutrition) {}

    /**
     * Smart Cart Nutrition (PRD §5.2).
     *
     * @param complete     false when any line lacks nutrition data. When false, every
     *                     total below is a LOWER BOUND, not the real figure, and
     *                     {@code healthScore} is null rather than a confident guess.
     * @param linesMissingData product names whose nutrition we do not have
     * @param healthScore  0-100, or null when the data is incomplete
     * @param warnings     imbalance flags; empty when nothing stands out
     */
    public record NutritionSummaryResponse(
            boolean complete,
            List<String> linesMissingData,
            BigDecimal totalWeightG,
            NutrientTotals totals,
            /** The basket's per-100g profile — what the score and warnings are computed from. */
            NutrientTotals per100g,
            Integer healthScore,
            String scoreBasis,
            List<Warning> warnings) {}

    public record NutrientTotals(
            BigDecimal caloriesKcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbohydratesG,
            BigDecimal fiberG,
            BigDecimal sugarG,
            BigDecimal ironMg,
            BigDecimal vitaminAMcg,
            BigDecimal vitaminCMg,
            BigDecimal vitaminDMcg,
            BigDecimal potassiumMg,
            BigDecimal sodiumMg) {}

    /**
     * @param level INFO | CAUTION | HIGH
     * @param nutrient which nutrient triggered it
     * @param message plain-language explanation
     */
    public record Warning(String level, String nutrient, String message) {}
}
