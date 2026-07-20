package com.ecoexpress.ai.repository;

import com.ecoexpress.ai.domain.PantryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PantryItemRepository extends JpaRepository<PantryItem, UUID> {

    /** A user's current pantry — not consumed. */
    @Query("""
            SELECT p FROM PantryItem p
            WHERE p.user.id = :userId AND p.consumedAt IS NULL
            ORDER BY p.expiryDate ASC NULLS LAST
            """)
    List<PantryItem> findActiveForUser(@Param("userId") UUID userId);

    /** Whether the user already has an unconsumed item by (case-insensitive) name — the
     *  "consume pantry before suggesting a purchase" check. */
    @Query("""
            SELECT count(p) > 0 FROM PantryItem p
            WHERE p.user.id = :userId AND p.consumedAt IS NULL
              AND lower(p.ingredientName) = lower(:name)
            """)
    boolean userHasIngredient(@Param("userId") UUID userId, @Param("name") String name);

    /** Items expiring within the window — feeds the expiry-reminder job (PRD §5.5). */
    @Query("""
            SELECT p FROM PantryItem p
            WHERE p.consumedAt IS NULL AND p.expiryDate IS NOT NULL
              AND p.expiryDate <= :before AND p.expiryNotifiedAt IS NULL
            """)
    List<PantryItem> findExpiringUnnotified(@Param("before") LocalDate before);

    /** Whether an order already seeded the pantry — makes delivery auto-stock idempotent. */
    @Query("""
            SELECT count(p) > 0 FROM PantryItem p
            WHERE p.sourceOrderId = :orderId
            """)
    boolean existsBySourceOrder(@Param("orderId") UUID orderId);
}
