package com.ecoexpress.payment.domain;

/** Mirrors refunds_status_chk in V5. */
public enum RefundStatus {
    /** Requested from the gateway; the money has not moved yet. */
    PENDING,
    /** The gateway confirmed it — only ever set from a webhook, never optimistically. */
    PROCESSED,
    FAILED
}
