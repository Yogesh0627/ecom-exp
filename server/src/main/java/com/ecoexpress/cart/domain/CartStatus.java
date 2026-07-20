package com.ecoexpress.cart.domain;

/** Mirrors carts_status_chk in V4. */
public enum CartStatus {
    ACTIVE,
    /** Became an order. */
    CONVERTED,
    /** Untouched past the abandon window (cart.abandon_after_hours setting). */
    ABANDONED,
    EXPIRED
}
