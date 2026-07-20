package com.ecoexpress.inventory.repository;

import com.ecoexpress.inventory.domain.InventoryBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, UUID> {

    /**
     * Batches with stock left, earliest expiry first — the FEFO allocation order.
     *
     * <p>{@code NULLS LAST} matters: a null expiry means non-perishable, and those must
     * be consumed only after everything that can spoil. Postgres sorts NULLs first by
     * default on ASC, which would do exactly the wrong thing.
     */
    @Query("""
            SELECT b FROM InventoryBatch b
            WHERE b.inventory.id = :inventoryId AND b.qtyRemaining > 0
            ORDER BY b.expiryDate ASC NULLS LAST, b.receivedAt ASC
            """)
    List<InventoryBatch> findFefoOrder(@Param("inventoryId") UUID inventoryId);

    Optional<InventoryBatch> findByInventoryIdAndLotNo(UUID inventoryId, String lotNo);

    /** Expiry-tracking alert (PRD §9): stock expiring within the window. */
    @Query("""
            SELECT b FROM InventoryBatch b
            WHERE b.qtyRemaining > 0 AND b.expiryDate IS NOT NULL AND b.expiryDate <= :before
            ORDER BY b.expiryDate ASC
            """)
    List<InventoryBatch> findExpiringBefore(@Param("before") LocalDate before);

    /** Already-expired stock still counted as on-hand — it must be written off. */
    @Query("""
            SELECT b FROM InventoryBatch b
            WHERE b.qtyRemaining > 0 AND b.expiryDate IS NOT NULL AND b.expiryDate < CURRENT_DATE
            """)
    List<InventoryBatch> findExpiredWithStock();
}
