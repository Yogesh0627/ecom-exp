package com.ecoexpress.ai.domain;

/** Where a pantry item came from (pantry_source_chk in V7). */
public enum PantrySource {
    MANUAL,
    /** Auto-added when an order was delivered. */
    ORDER,
    /** Added from a Smart Fridge scan. */
    FRIDGE_SCAN
}
