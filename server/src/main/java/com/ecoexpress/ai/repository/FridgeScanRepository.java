package com.ecoexpress.ai.repository;

import com.ecoexpress.ai.domain.FridgeScan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FridgeScanRepository extends JpaRepository<FridgeScan, UUID> {

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product"})
    @Query("SELECT s FROM FridgeScan s WHERE s.id = :id")
    Optional<FridgeScan> findByIdWithItems(@Param("id") UUID id);

    /**
     * Scans whose image is past its retention date and not yet purged — the PII retention job's
     * work list. Matches fridge_scans_purge_idx.
     */
    @Query("SELECT s FROM FridgeScan s WHERE s.imagePurgedAt IS NULL AND s.purgeAfter <= :now")
    List<FridgeScan> findImagesToPurge(@Param("now") Instant now);
}
