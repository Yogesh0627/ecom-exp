package com.ecoexpress.identity.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstrap admin settings, from {@code ecoexpress.bootstrap.*}.
 * Supplied via environment/.env — never hardcoded.
 */
@ConfigurationProperties(prefix = "ecoexpress.bootstrap")
public record AdminBootstrapProperties(
        String adminEmail,
        String adminPassword,
        String adminName) {

    public AdminBootstrapProperties {
        if (adminName == null || adminName.isBlank()) {
            adminName = "Administrator";
        }
    }
}
