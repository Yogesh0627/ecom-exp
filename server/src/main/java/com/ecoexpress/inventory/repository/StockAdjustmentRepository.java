package com.ecoexpress.inventory.repository;

import com.ecoexpress.inventory.domain.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {

    Page<StockAdjustment> findByInventoryIdOrderByCreatedAtDesc(UUID inventoryId, Pageable pageable);

    /** Pending (unapproved) adjustments — the approval queue. */
    @Query("SELECT a FROM StockAdjustment a WHERE a.approvedAt IS NULL ORDER BY a.createdAt")
    Page<StockAdjustment> findPending(Pageable pageable);
}
