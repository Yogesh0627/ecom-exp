package com.ecoexpress.common.security;

import com.ecoexpress.common.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates JWTs, and mints refresh tokens.
 *
 * <p>Access tokens are stateless and carry the user's permissions, so authorizing a
 * request needs no database round trip. The cost of that is revocation: a leaked access
 * token stays valid until it expires, which is why the TTL is 15 minutes.
 *
 * <p>Refresh tokens are opaque random strings, not JWTs — they are looked up server-side
 * anyway, so there is nothing to gain from a signed structure and a real cost to
 * self-describing tokens.
 */
@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_PERMISSIONS = "perms";
    private static final String CLAIM_EMAIL = "email";
    private static final String TOKEN_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";

    private final JwtProperties props;
    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, String email, List<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(props.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTokenTtl())))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_PERMISSIONS, permissions)
                .claim(TOKEN_TYPE, TYPE_ACCESS)
                .signWith(key)
                .compact();
    }

    /**
     * A 256-bit random string. Opaque: it means nothing without the matching DB row.
     */
    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, unsalted and deliberately so. This is a 256-bit random value, not a
     * password: it has no guessable structure, so there is nothing for a rainbow table
     * to precompute and no reason to pay bcrypt's cost on every token refresh. Hashing
     * at all is what stops a database leak from handing out live sessions.
     */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            // SHA-256 is mandated by the JVM spec; if it is missing the platform is broken.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Returns the claims of a valid access token, or empty for anything else.
     *
     * <p>Every failure mode collapses to empty on purpose: an expired token, a forged
     * signature and a refresh token presented as an access token are all simply
     * "not authenticated" to the caller.
     */
    public Optional<Claims> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(props.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TYPE_ACCESS.equals(claims.get(TOKEN_TYPE, String.class))) {
                log.debug("Rejected token: wrong type claim");
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (ExpiredJwtException e) {
            log.debug("Rejected token: expired");
            return Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            // Do not log the token itself — it is a credential.
            log.debug("Rejected token: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(props.refreshTokenTtl());
    }

    /** Lets the login response report expires_in without reaching into config. */
    public long accessTokenTtlSeconds() {
        return props.accessTokenTtl().toSeconds();
    }
}
