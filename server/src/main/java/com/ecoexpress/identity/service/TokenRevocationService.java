package com.ecoexpress.identity.service;

import com.ecoexpress.identity.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Revokes refresh tokens in their OWN transaction.
 *
 * <p>This exists for one reason. When {@code AuthService.refresh} detects a replayed
 * token it must (a) revoke every session for that user and (b) fail the request. Doing
 * both in one transaction means the thrown exception rolls the revocation back — the
 * attacker is told "no", and their stolen token keeps working. The security control
 * silently undoes itself.
 *
 * <p>REQUIRES_NEW suspends the caller's transaction and commits the revocation
 * independently, so it survives the rollback. It is a separate bean because Spring's
 * proxying means a self-invoked {@code this.method()} would ignore the annotation
 * entirely.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;

    /** Commits even if the calling transaction rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllForUser(UUID userId) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        log.info("Revoked {} refresh token(s) for user {}", revoked, userId);
        return revoked;
    }
}
