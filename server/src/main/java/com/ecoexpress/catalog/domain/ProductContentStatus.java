package com.ecoexpress.catalog.domain;

/**
 * Publication state of a product's rich content (product_content_status_chk in V11).
 *
 * <p>AI writes a {@link #DRAFT}; only a human-approved {@link #PUBLISHED} row is ever shown to
 * shoppers. This is the guardrail against a plausible-but-wrong health claim reaching a customer.
 */
public enum ProductContentStatus {
    DRAFT,
    PUBLISHED
}
