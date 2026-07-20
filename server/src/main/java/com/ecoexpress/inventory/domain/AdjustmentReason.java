package com.ecoexpress.inventory.domain;

/** Reason codes for a manual stock adjustment (adjustment_reason_chk in V3). */
public enum AdjustmentReason {
    /** Physically damaged, unsellable. */
    DAMAGE,
    /** Past expiry. */
    EXPIRY,
    /** A physical count disagreed with the system; this reconciles it. */
    COUNT_CORRECTION,
    THEFT,
    /** Given away as a sample. */
    SAMPLE,
    OTHER
}
