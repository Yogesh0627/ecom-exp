package com.ecoexpress.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS origins, bound from {@code ecoexpress.cors.allowed-origins}.
 *
 * <p>Configuration rather than a constant: the Next.js storefront runs on :3000 locally
 * and on a real domain in production, and those must not be hardcoded together.
 */
@ConfigurationProperties(prefix = "ecoexpress.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            // Local Next.js dev server. Production overrides via ECOEXPRESS_CORS_ALLOWED_ORIGINS.
            allowedOrigins = List.of("http://localhost:3000");
        }
    }
}
