package com.ecoexpress.promotion.domain;

/** Mirrors coupons_type_chk in V5. */
public enum CouponType {
    /** A percentage off the subtotal, optionally capped by max_discount. */
    PERCENT,
    /** A flat rupee amount off. */
    FLAT,
    /** Waives the shipping fee. */
    FREE_SHIPPING
}
