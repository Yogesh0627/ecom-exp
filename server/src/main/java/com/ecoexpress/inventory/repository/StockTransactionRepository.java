package com.ecoexpress.inventory.repository;

import com.ecoexpress.inventory.domain.StockTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Ledger reads only. There is deliberately no update or delete method: the table is
 * append-only and a DB trigger rejects both anyway.
 */
public interface StockTransactionRepository extends JpaRepository<StockTransaction, UUID> {

    Page<StockTransaction> findByInventoryIdOrderByCreatedAtDesc(UUID inventoryId, Pageable pageable);

    /** Every movement caused by one order/PO — the audit trail for a dispute. */
    List<StockTransaction> findByRefTypeAndRefId(String refType, UUID refId);

    /**
     * Recomputes on-hand from the ledger, ignoring reservations (they never moved
     * physical stock). This is the independent check against inventory.qty_on_hand.
     */
    @Query("""
            SELECT coalesce(sum(t.qtyDelta), 0) FROM StockTransaction t
            WHERE t.inventory.id = :inventoryId
              AND t.type NOT IN (com.ecoexpress.inventory.domain.StockTransactionType.RESERVATION,
                                 com.ecoexpress.inventory.domain.StockTransactionType.RELEASE)
            """)
    int replayOnHand(@Param("inventoryId") UUID inventoryId);
}
