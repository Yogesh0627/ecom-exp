package com.ecoexpress.payment.domain;

/** Mirrors payments_status_chk in V5. */
public enum PaymentStatus {
    /** Gateway order created; the customer has not paid yet. */
    CREATED,
    /** Funds held but not taken. */
    AUTHORIZED,
    /** Money is ours. */
    CAPTURED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED
}
