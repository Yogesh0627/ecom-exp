package com.ecoexpress.ai.service;

import com.ecoexpress.ai.client.AiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Live AI availability, so the UI can tell users when AI features are temporarily unavailable
 * (over the provider's usage/quota limit) instead of showing a generic failure.
 *
 * <p>Deliberately in-memory and best-effort: the provider is the source of truth, but when it
 * returns a rate-limit/quota error we remember a short cooldown so the storefront can show a clear
 * "try again in ~Ns" banner without hammering the provider to find out.
 *
 * <p><b>Single-instance assumption</b>, like the schedulers: on multiple app instances each tracks
 * its own last-seen limit. Fine at launch scale; a shared cache would be needed to scale out.
 */
@Service
@RequiredArgsConstructor
public class AiStatusService {

    /** Fallback cooldown when the provider gives no explicit "retry in Ns" hint. */
    private static final int DEFAULT_COOLDOWN_SECONDS = 60;

    private final AiClient client;

    private volatile Instant rateLimitedUntil = Instant.EPOCH;
    private volatile String lastMessage;

    /** Called when a provider call is rejected for rate-limit/quota. */
    public void markRateLimited(int retryAfterSeconds, String message) {
        int seconds = retryAfterSeconds > 0 ? retryAfterSeconds : DEFAULT_COOLDOWN_SECONDS;
        this.rateLimitedUntil = Instant.now().plusSeconds(seconds);
        this.lastMessage = message;
    }

    public AiStatus status() {
        boolean enabled = client.isAvailable();
        Instant now = Instant.now();
        boolean rateLimited = now.isBefore(rateLimitedUntil);
        long retryAfter = rateLimited
                ? Math.max(1, Duration.between(now, rateLimitedUntil).getSeconds())
                : 0;
        String message;
        if (!enabled) {
            message = "AI features are not configured on this environment.";
        } else if (rateLimited) {
            message = lastMessage != null ? lastMessage
                    : "AI features are over their usage limit right now. Please try again shortly.";
        } else {
            message = null;
        }
        return new AiStatus(enabled, enabled && !rateLimited, rateLimited, retryAfter, message);
    }

    /**
     * @param enabled          whether an AI provider is configured at all
     * @param available        enabled AND not currently rate-limited
     * @param rateLimited       true while inside a known cooldown
     * @param retryAfterSeconds seconds until the cooldown ends (0 when not rate-limited)
     * @param message           user-facing explanation, or null when all is well
     */
    public record AiStatus(boolean enabled, boolean available, boolean rateLimited,
                           long retryAfterSeconds, String message) {}
}
