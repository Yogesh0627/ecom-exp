package com.ecoexpress.promotion.repository;

import com.ecoexpress.promotion.domain.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, UUID> {

    /** How many times this user has already redeemed this coupon — enforces the per-user cap. */
    @Query("""
            SELECT count(r) FROM CouponRedemption r
            WHERE r.coupon.id = :couponId AND r.user.id = :userId
            """)
    long countByCouponAndUser(@Param("couponId") UUID couponId, @Param("userId") UUID userId);

    boolean existsByCouponIdAndOrderId(UUID couponId, UUID orderId);

    /** Whether this user has any prior order — enforces first_order_only. */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM orders o
                WHERE o.user_id = :userId AND o.deleted_at IS NULL
                  AND o.status <> 'CANCELLED'
            )
            """, nativeQuery = true)
    boolean userHasPriorOrder(@Param("userId") UUID userId);
}
