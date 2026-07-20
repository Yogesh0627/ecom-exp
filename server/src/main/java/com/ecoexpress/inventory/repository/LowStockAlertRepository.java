package com.ecoexpress.inventory.repository;

import com.ecoexpress.inventory.domain.LowStockAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LowStockAlertRepository extends JpaRepository<LowStockAlert, UUID> {

    /** The single open alert for an inventory row, if any (partial unique index guarantees ≤1). */
    @Query("SELECT a FROM LowStockAlert a WHERE a.inventory.id = :inventoryId AND a.resolvedAt IS NULL")
    Optional<LowStockAlert> findOpenForInventory(@Param("inventoryId") UUID inventoryId);

    @Query("SELECT a FROM LowStockAlert a WHERE a.resolvedAt IS NULL ORDER BY a.triggeredAt")
    List<LowStockAlert> findAllOpen();

    /** Open alerts not yet pushed to a notification channel. */
    @Query("SELECT a FROM LowStockAlert a WHERE a.resolvedAt IS NULL AND a.notifiedAt IS NULL")
    List<LowStockAlert> findUnnotified();
}
