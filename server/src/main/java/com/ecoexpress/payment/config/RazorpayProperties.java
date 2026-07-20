package com.ecoexpress.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay credentials, from {@code ecoexpress.razorpay.*}.
 *
 * @param keyId         public key id (rzp_test_… / rzp_live_…), safe to send to the browser
 * @param keySecret     API secret — signs the checkout callback. NEVER leaves the server.
 * @param webhookSecret separate secret configured in the Razorpay dashboard; signs webhooks
 * @param enabled       false until real credentials exist, so the app boots without them
 */
@ConfigurationProperties(prefix = "ecoexpress.razorpay")
public record RazorpayProperties(
        String keyId,
        String keySecret,
        String webhookSecret,
        Boolean enabled) {

    public RazorpayProperties {
        if (enabled == null) {
            // Enabled only when a key id is actually configured. Defaulting to true would
            // mean a fresh checkout fails at runtime with a confusing gateway error
            // instead of a clear "payments are not configured".
            enabled = keyId != null && !keyId.isBlank();
        }
    }

    public boolean isConfigured() {
        return Boolean.TRUE.equals(enabled)
                && keyId != null && !keyId.isBlank()
                && keySecret != null && !keySecret.isBlank();
    }
}
