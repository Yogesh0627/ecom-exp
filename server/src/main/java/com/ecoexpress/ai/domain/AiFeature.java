package com.ecoexpress.ai.domain;

/** Which hero feature made an AI call (ai_log_feature_chk in V7). */
public enum AiFeature {
    FRIDGE_SCAN,
    MEAL_PLAN,
    RECIPE_GEN,
    NUTRITION_ESTIMATE,
    RECOMMENDATION,
    SEARCH_ENHANCE,
    /** Generating rich product content (advantages, health benefits, why-choose). */
    PRODUCT_CONTENT,
    /** "Turn my cart into a meal" — recipe completion from cart + pantry items. */
    RECIPE_COMPLETION
}
