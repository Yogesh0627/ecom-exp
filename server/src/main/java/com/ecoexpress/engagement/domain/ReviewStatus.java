package com.ecoexpress.engagement.domain;

/** Mirrors reviews_status_chk in V6. Only APPROVED reviews appear on the product page. */
public enum ReviewStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Reported by users; back in the moderation queue. */
    FLAGGED
}
