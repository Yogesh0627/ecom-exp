package com.ecoexpress.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT settings, bound from {@code ecoexpress.jwt.*}.
 *
 * @param secret         HS256 signing key. Must be >= 32 bytes; validated at startup.
 * @param accessTokenTtl short by design — a leaked access token cannot be revoked, so
 *                       its blast radius is bounded by expiry alone.
 * @param refreshTokenTtl long-lived, but revocable: refresh tokens are stored server-side.
 * @param issuer         the "iss" claim.
 */
@Validated
@ConfigurationProperties(prefix = "ecoexpress.jwt")
public record JwtProperties(
        @NotBlank String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String issuer) {

    public JwtProperties {
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = Duration.ofDays(30);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "ecoexpress";
        }
        // Fail at startup, not on the first login. HS256 with a key shorter than the
        // hash output is a real weakness, and jjwt would throw at signing time anyway —
        // better to refuse to boot than to boot and 500 on every login.
        if (secret != null && secret.getBytes().length < 32) {
            throw new IllegalStateException(
                    "ecoexpress.jwt.secret must be at least 32 bytes for HS256 (got "
                            + secret.getBytes().length + ").");
        }
    }
}
