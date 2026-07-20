package com.ecoexpress.promotion.service;

import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.identity.domain.User;
import com.ecoexpress.order.domain.Order;
import com.ecoexpress.promotion.domain.Coupon;
import com.ecoexpress.promotion.domain.CouponRedemption;
import com.ecoexpress.promotion.repository.CouponRedemptionRepository;
import com.ecoexpress.promotion.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Coupon validation and redemption.
 *
 * <p>Validation runs the full gauntlet in a fixed order so the customer gets the most useful
 * message: exists → active/in-window → min cart → per-user cap → total cap → first-order-only.
 * Each check is cheap and the order is deliberate — "this coupon expired" is more useful than
 * "you have used this coupon too many times" when both are true.
 *
 * <p><b>Validation is advisory; redemption is authoritative.</b> {@link #quote} tells the cart
 * what a coupon WOULD save, with no side effects. {@link #redeem} is called inside the checkout
 * transaction, takes a row lock, re-checks the caps, and writes the redemption ledger row — so a
 * coupon cannot be over-redeemed by two concurrent checkouts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;

    /** The outcome of pricing a coupon against a cart. */
    public record CouponQuote(
            Coupon coupon, BigDecimal discount, boolean freeShipping) {}

    /**
     * Validates a coupon for a user + cart subtotal and returns what it would save.
     * Read-only: no ledger row, no counter change.
     */
    @Transactional(readOnly = true)
    public CouponQuote quote(String code, UUID userId, BigDecimal subtotal) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new BadRequestException("That coupon code is not valid."));
        validate(coupon, userId, subtotal);
        return new CouponQuote(coupon, coupon.discountFor(subtotal), coupon.isFreeShipping());
    }

    /**
     * Records a redemption inside the checkout transaction.
     *
     * <p>Row-locks the coupon, re-runs the caps under the lock (the state may have changed since
     * {@link #quote}), writes the append-only ledger row, and bumps the cached counter. The
     * UNIQUE(coupon_id, order_id) constraint is the final backstop against applying one coupon to
     * an order twice.
     */
    @Transactional
    public BigDecimal redeem(String code, User user, Order order, BigDecimal subtotal) {
        Coupon locked = couponRepository.findByCodeIgnoreCase(code)
                .flatMap(c -> couponRepository.findByIdForUpdate(c.getId()))
                .orElseThrow(() -> new BadRequestException("That coupon code is not valid."));

        validate(locked, user.getId(), subtotal);

        if (redemptionRepository.existsByCouponIdAndOrderId(locked.getId(), order.getId())) {
            throw new BadRequestException("This coupon is already applied to the order.");
        }

        BigDecimal discount = locked.discountFor(subtotal);

        redemptionRepository.save(CouponRedemption.builder()
                .coupon(locked)
                .user(user)
                .order(order)
                .discountApplied(discount)
                .redeemedAt(Instant.now())
                .build());
        locked.setTimesUsed(locked.getTimesUsed() + 1);

        log.info("Coupon {} redeemed by {} on order {}: -{}",
                locked.getCode(), user.getId(), order.getOrderNumber(), discount);
        return discount;
    }

    /**
     * The full validation gauntlet. Throws a specific BadRequest on the first failure.
     */
    private void validate(Coupon coupon, UUID userId, BigDecimal subtotal) {
        Instant now = Instant.now();

        if (!coupon.isLiveAt(now)) {
            // Distinguish "expired/not started" from "switched off" for a clearer message.
            if (Boolean.FALSE.equals(coupon.getIsActive())) {
                throw new BadRequestException("That coupon is no longer available.");
            }
            if (now.isBefore(coupon.getValidFrom())) {
                throw new BadRequestException("That coupon is not active yet.");
            }
            throw new BadRequestException("That coupon has expired.");
        }
        if (subtotal.compareTo(coupon.getMinCartValue()) < 0) {
            throw new BadRequestException(
                    "Add items worth at least " + coupon.getMinCartValue() + " to use this coupon.");
        }
        long userUses = redemptionRepository.countByCouponAndUser(coupon.getId(), userId);
        if (userUses >= coupon.getMaxUsesPerUser()) {
            throw new BadRequestException("You have already used this coupon.");
        }
        if (coupon.getMaxUses() != null && coupon.getTimesUsed() >= coupon.getMaxUses()) {
            throw new BadRequestException("This coupon has reached its usage limit.");
        }
        if (Boolean.TRUE.equals(coupon.getFirstOrderOnly())
                && redemptionRepository.userHasPriorOrder(userId)) {
            throw new BadRequestException("This coupon is for your first order only.");
        }
    }

    // ---------- admin ----------

    @Transactional
    public Coupon create(Coupon coupon) {
        if (couponRepository.existsByCodeIgnoreCase(coupon.getCode())) {
            throw new BadRequestException("A coupon with that code already exists.");
        }
        if (coupon.getValidUntil().isBefore(coupon.getValidFrom())) {
            throw new BadRequestException("valid_until must be after valid_from.");
        }
        return couponRepository.save(coupon);
    }

    @Transactional
    public void deactivate(UUID couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new NotFoundException("No coupon " + couponId));
        coupon.setIsActive(false);
    }
}
