package com.ecoexpress.engagement.repository;

import com.ecoexpress.engagement.domain.Review;
import com.ecoexpress.engagement.domain.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    @EntityGraph(attributePaths = {"images", "user"})
    Page<Review> findByProductIdAndStatus(UUID productId, ReviewStatus status, Pageable pageable);

    Page<Review> findByStatusOrderByCreatedAtAsc(ReviewStatus status, Pageable pageable);

    Page<Review> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * The verified-purchase check, and the whole point of the badge.
     *
     * <p>Returns a delivered order line for this user and product, if one exists. Native query
     * because it crosses into the order module's tables (orders, order_items) which the review
     * entity graph does not — and deliberately reads the order's own state, not a claim on the
     * request. {@code product_variants} joins order_items back to the product because an order
     * line references a variant, and a variant belongs to a product.
     */
    @Query(value = """
            SELECT oi.id
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            JOIN product_variants v ON v.id = oi.variant_id
            WHERE o.user_id = :userId
              AND v.product_id = :productId
              AND o.status = 'DELIVERED'
              AND o.deleted_at IS NULL
              AND oi.deleted_at IS NULL
            LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findDeliveredOrderItem(@Param("userId") UUID userId,
                                          @Param("productId") UUID productId);
}
