package com.ecoexpress.engagement.domain;

/** Mirrors notifications_type_chk in V6. */
public enum NotificationType {
    ORDER_PLACED,
    ORDER_SHIPPED,
    ORDER_DELIVERED,
    ORDER_CANCELLED,
    PAYMENT_FAILED,
    REFUND_PROCESSED,
    PANTRY_EXPIRY,
    BACK_IN_STOCK,
    PRICE_DROP,
    MEAL_PLAN_READY,
    PROMO,
    SYSTEM
}
