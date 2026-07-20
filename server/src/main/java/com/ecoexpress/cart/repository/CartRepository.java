package com.ecoexpress.cart.repository;

import com.ecoexpress.cart.domain.Cart;
import com.ecoexpress.cart.domain.CartStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    /**
     * The user's active cart with its lines, variants and each variant's nutrition.
     *
     * <p>Nutrition is fetched here because the cart screen shows the Smart Cart summary
     * (PRD §5.2) on every render — leaving it lazy is an N+1 per line. Images are NOT
     * fetched: they are a second bag and would trigger MultipleBagFetchException;
     * @BatchSize on the collection handles them.
     */
    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.nutritionFacts",
                                   "items.variant.product"})
    @Query("SELECT c FROM Cart c WHERE c.user.id = :userId AND c.status = :status")
    Optional<Cart> findByUserAndStatus(@Param("userId") UUID userId,
                                       @Param("status") CartStatus status);

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.nutritionFacts",
                                   "items.variant.product"})
    @Query("SELECT c FROM Cart c WHERE c.sessionKey = :key AND c.status = :status")
    Optional<Cart> findBySessionKeyAndStatus(@Param("key") String sessionKey,
                                             @Param("status") CartStatus status);

    /** Carts untouched past the abandon window — feeds the abandoned-cart job. */
    @Query("""
            SELECT c FROM Cart c
            WHERE c.status = com.ecoexpress.cart.domain.CartStatus.ACTIVE
              AND c.updatedAt < :cutoff
            """)
    List<Cart> findStaleActiveCarts(@Param("cutoff") Instant cutoff);
}
