package com.ecoexpress.identity.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo customer settings, from {@code ecoexpress.bootstrap.demo.*}.
 *
 * <p>This is a public showcase build: a pre-verified demo customer is seeded on boot so the
 * storefront's one-click "sign in as User" works instantly (no signup, no email-verification
 * banner). Disable with {@code enabled: false} for a real, private deployment.
 */
@ConfigurationProperties(prefix = "ecoexpress.bootstrap.demo")
public record DemoAccountProperties(
        boolean enabled,
        String email,
        String password,
        String name) {

    public DemoAccountProperties {
        if (name == null || name.isBlank()) {
            name = "Demo Customer";
        }
    }
}
