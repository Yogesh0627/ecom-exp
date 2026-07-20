package com.ecoexpress.inventory.repository;

import com.ecoexpress.inventory.domain.PurchaseOrder;
import com.ecoexpress.inventory.domain.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @EntityGraph(attributePaths = {"items", "items.variant", "supplier", "warehouse"})
    @Query("SELECT po FROM PurchaseOrder po WHERE po.id = :id")
    Optional<PurchaseOrder> findByIdWithItems(@Param("id") UUID id);

    boolean existsByPoNumber(String poNumber);

    Page<PurchaseOrder> findByStatusOrderByCreatedAtDesc(PurchaseOrderStatus status, Pageable pageable);

    Page<PurchaseOrder> findBySupplierIdOrderByCreatedAtDesc(UUID supplierId, Pageable pageable);

    /** Admin listing: all POs newest first, with supplier/warehouse for the summary row. */
    @EntityGraph(attributePaths = {"supplier", "warehouse", "items"})
    Page<PurchaseOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Today's PO count, for the daily sequence in the PO number. */
    @Query("SELECT count(po) FROM PurchaseOrder po WHERE po.createdAt >= :dayStart")
    long countSince(@Param("dayStart") Instant dayStart);
}
