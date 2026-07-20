package com.ecoexpress.identity.repository;

import com.ecoexpress.identity.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /** Invalidate any outstanding change-email links for a user (cancelling a pending change). */
    @Modifying
    @Query("""
            UPDATE EmailVerificationToken t SET t.usedAt = :now
            WHERE t.user.id = :userId AND t.newEmail IS NOT NULL AND t.usedAt IS NULL
            """)
    int expirePendingChangeTokens(@Param("userId") UUID userId, @Param("now") Instant now);
}
