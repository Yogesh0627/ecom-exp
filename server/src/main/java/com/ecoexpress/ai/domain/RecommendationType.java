package com.ecoexpress.ai.domain;

/** Mirrors rec_rule_type_chk in V7. */
public enum RecommendationType {
    /** Goes well with — Paneer → Peas. */
    COMPLEMENT,
    /** An alternative if the trigger is out of stock. */
    SUBSTITUTE,
    FREQUENTLY_BOUGHT_TOGETHER,
    UPSELL,
    /** Suggested because it completes a recipe. */
    RECIPE_BASED
}
