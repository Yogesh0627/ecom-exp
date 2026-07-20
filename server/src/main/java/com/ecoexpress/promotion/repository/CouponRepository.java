package com.ecoexpress.promotion.repository;

import com.ecoexpress.promotion.domain.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    @Query("SELECT c FROM Coupon c WHERE upper(c.code) = upper(:code)")
    Optional<Coupon> findByCodeIgnoreCase(@Param("code") String code);

    /** Admin listing: newest validity window first. */
    java.util.List<Coupon> findAllByOrderByValidFromDesc();

    boolean existsByCodeIgnoreCase(String code);

    /**
     * Locks the coupon row while a redemption is recorded.
     *
     * <p>Without the lock, two orders redeeming the last use of a limited coupon both read
     * {@code times_used < max_uses} and both proceed — the coupon is over-redeemed. The lock
     * serialises the check-and-increment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") UUID id);
}
