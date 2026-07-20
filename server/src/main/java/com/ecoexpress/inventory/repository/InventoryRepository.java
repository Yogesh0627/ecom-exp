package com.ecoexpress.inventory.repository;

import com.ecoexpress.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    /**
     * Locks the stock row for the rest of the transaction. <b>Required for any
     * reservation or decrement.</b>
     *
     * <p>Without the row lock, two concurrent buyers of the last unit both read
     * available=1, both decide "yes", and both write — one customer gets an apology
     * instead of an order. The CHECK constraint
     * ({@code inventory_reserved_lte_on_hand}) would catch the write, but only as a
     * 500; the lock makes the second request wait and see the truth.
     *
     * <p>The 3s timeout keeps a stuck transaction from pinning request threads forever.
     * Postgres raises a lock timeout rather than blocking indefinitely.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            SELECT i FROM Inventory i
            WHERE i.variant.id = :variantId AND i.warehouse.id = :warehouseId
            """)
    Optional<Inventory> findForUpdate(@Param("variantId") UUID variantId,
                                      @Param("warehouseId") UUID warehouseId);

    @Query("""
            SELECT i FROM Inventory i
            WHERE i.variant.id = :variantId AND i.warehouse.id = :warehouseId
            """)
    Optional<Inventory> find(@Param("variantId") UUID variantId,
                             @Param("warehouseId") UUID warehouseId);

    /** Total sellable units of a variant across every warehouse. */
    @Query("""
            SELECT coalesce(sum(i.qtyOnHand - i.qtyReserved), 0) FROM Inventory i
            WHERE i.variant.id = :variantId
            """)
    int totalAvailable(@Param("variantId") UUID variantId);

    @Query("SELECT i FROM Inventory i WHERE i.variant.id = :variantId")
    List<Inventory> findByVariant(@Param("variantId") UUID variantId);

    /** Low-stock report (PRD §9). */
    @Query("SELECT i FROM Inventory i WHERE i.qtyOnHand <= i.reorderPoint")
    List<Inventory> findBelowReorderPoint();

    /**
     * Rows where the cached qty_on_hand disagrees with the ledger. Should always be
     * empty; anything here is an accuracy bug (PRD §2). Backed by the
     * inventory_ledger_drift view from V3.
     */
    @Query(value = "SELECT inventory_id, cached_qty, ledger_qty, drift FROM inventory_ledger_drift",
           nativeQuery = true)
    List<Object[]> findLedgerDrift();
}
