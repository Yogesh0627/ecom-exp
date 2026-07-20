package com.ecoexpress.identity.repository;

import com.ecoexpress.identity.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Locks the row for the duration of the rotation transaction.
     *
     * <p>Without PESSIMISTIC_WRITE, two concurrent refreshes with the same token both
     * read it as usable and both mint a replacement — the theft-detection check never
     * fires, and a stolen token silently keeps working alongside the real one.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes every live token for a user. Used on logout-everywhere and — critically —
     * when a replayed token proves a leak.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken rt SET rt.revokedAt = :now
            WHERE rt.user.id = :userId AND rt.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Cleanup job: expired tokens carry no value and are hard-deleted. */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
