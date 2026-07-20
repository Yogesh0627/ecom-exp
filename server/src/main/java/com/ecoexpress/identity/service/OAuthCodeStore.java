package com.ecoexpress.identity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, single-use codes bridging the OAuth redirect and the token exchange.
 *
 * <p>After Google auth, the success handler mints a code for the resolved user and redirects the
 * browser to the storefront with it; the storefront POSTs the code back to exchange it for the JWT.
 * The code carries no token — only a reference to the user — so nothing sensitive ever rides in the
 * URL, and it is consumed on first use with a 2-minute TTL.
 *
 * <p>In-memory: fine for a single instance. A multi-instance deploy should back this with Redis so
 * the code issued by one node is redeemable at another.
 */
@Slf4j
@Component
public class OAuthCodeStore {

    private static final Duration TTL = Duration.ofMinutes(2);
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Entry> codes = new ConcurrentHashMap<>();

    private record Entry(UUID userId, Instant expiresAt) {}

    /** Mints a one-time code for a user. */
    public String issue(UUID userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codes.put(code, new Entry(userId, Instant.now().plus(TTL)));
        sweepIfLarge();
        return code;
    }

    /** Consumes a code, returning its user id if the code is valid and unexpired. Single-use. */
    public Optional<UUID> consume(String code) {
        if (code == null) {
            return Optional.empty();
        }
        Entry entry = codes.remove(code);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    /** Opportunistic cleanup so abandoned codes do not accumulate. */
    private void sweepIfLarge() {
        if (codes.size() > 1000) {
            Instant now = Instant.now();
            codes.values().removeIf(e -> e.expiresAt().isBefore(now));
        }
    }
}
