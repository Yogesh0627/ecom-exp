package com.ecoexpress.catalog.domain;

/**
 * Where a nutrition figure came from (nutrition_source_chk in V2).
 *
 * <p>IFCT = Indian Food Composition Tables; USDA = FoodData Central. AI_ESTIMATED
 * values are guesses and must never be presented as measured facts.
 */
public enum NutritionSource {
    IFCT,
    USDA,
    LABEL,
    MANUAL,
    AI_ESTIMATED
}
