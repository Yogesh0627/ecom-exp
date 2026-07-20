package com.ecoexpress.order.repository;

import com.ecoexpress.order.domain.Order;
import com.ecoexpress.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = {"items", "items.variant"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** My-orders filtered to a status bucket (in-progress / delivered / cancelled). */
    Page<Order> findByUserIdAndStatusInOrderByCreatedAtDesc(
            UUID userId, java.util.Collection<OrderStatus> statuses, Pageable pageable);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    /** Admin listing: all orders, newest first. */
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Unpaid orders past their hold window. Their reservations must be released or the
     * stock is stranded — an abandoned checkout would silently remove units from sale
     * forever.
     */
    @EntityGraph(attributePaths = {"items", "items.variant"})
    @Query("""
            SELECT o FROM Order o
            WHERE o.status = com.ecoexpress.order.domain.OrderStatus.PENDING_PAYMENT
              AND o.createdAt < :cutoff
            """)
    List<Order> findExpiredPendingPayment(@Param("cutoff") Instant cutoff);

    /** Today's order count, for generating the daily sequence in the order number. */
    @Query("SELECT count(o) FROM Order o WHERE o.createdAt >= :dayStart")
    long countSince(@Param("dayStart") Instant dayStart);
}
