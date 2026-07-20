package com.ecoexpress.promotion.api;

import com.ecoexpress.common.security.AuthenticatedUser;
import com.ecoexpress.promotion.domain.Coupon;
import com.ecoexpress.promotion.domain.CouponType;
import com.ecoexpress.promotion.service.CouponService;
import com.ecoexpress.promotion.service.CouponService.CouponQuote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.ecoexpress.promotion.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Coupons")
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final CouponRepository couponRepository;

    public record CouponRow(
            UUID id, String code, String description, CouponType type, BigDecimal value,
            BigDecimal maxDiscount, BigDecimal minCartValue, Instant validFrom, Instant validUntil,
            Integer maxUses, Integer timesUsed, Integer maxUsesPerUser, boolean firstOrderOnly,
            boolean isActive) {}

    public record QuoteRequest(
            @NotBlank String code,
            @NotNull @DecimalMin("0.00") BigDecimal cartSubtotal) {}

    public record CreateCouponRequest(
            @NotBlank String code,
            String description,
            @NotNull CouponType type,
            @NotNull @DecimalMin("0.00") BigDecimal value,
            BigDecimal maxDiscount,
            BigDecimal minCartValue,
            @NotNull Instant validFrom,
            @NotNull Instant validUntil,
            Integer maxUses,
            Integer maxUsesPerUser,
            Boolean firstOrderOnly) {}

    /**
     * Tells the cart what a coupon would save, without redeeming it. This is what the "Apply
     * coupon" button calls — a preview. The authoritative redemption happens at checkout.
     */
    @Operation(summary = "Preview a coupon against the current cart subtotal")
    @PostMapping("/quote")
    public ResponseEntity<Map<String, Object>> quote(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody QuoteRequest r) {
        CouponQuote q = couponService.quote(r.code(), user.getId(), r.cartSubtotal());
        return ResponseEntity.ok(Map.of(
                "code", q.coupon().getCode(),
                "discount", q.discount(),
                "freeShipping", q.freeShipping(),
                "description", q.coupon().getDescription() == null ? "" : q.coupon().getDescription()));
    }

    @Operation(summary = "List all coupons (admin)")
    @GetMapping
    @PreAuthorize("hasAuthority('coupon:write')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CouponRow>> list() {
        return ResponseEntity.ok(couponRepository.findAllByOrderByValidFromDesc().stream()
                .map(c -> new CouponRow(
                        c.getId(), c.getCode(), c.getDescription(), c.getType(), c.getValue(),
                        c.getMaxDiscount(), c.getMinCartValue(), c.getValidFrom(), c.getValidUntil(),
                        c.getMaxUses(), c.getTimesUsed(), c.getMaxUsesPerUser(),
                        Boolean.TRUE.equals(c.getFirstOrderOnly()), Boolean.TRUE.equals(c.getIsActive())))
                .toList());
    }

    @Operation(summary = "Create a coupon (admin)")
    @PostMapping
    @PreAuthorize("hasAuthority('coupon:write')")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateCouponRequest r) {
        Coupon coupon = couponService.create(Coupon.builder()
                .code(r.code())
                .description(r.description())
                .type(r.type())
                .value(r.value())
                .maxDiscount(r.maxDiscount())
                .minCartValue(r.minCartValue() == null ? BigDecimal.ZERO : r.minCartValue())
                .validFrom(r.validFrom())
                .validUntil(r.validUntil())
                .maxUses(r.maxUses())
                .maxUsesPerUser(r.maxUsesPerUser() == null ? 1 : r.maxUsesPerUser())
                .firstOrderOnly(Boolean.TRUE.equals(r.firstOrderOnly()))
                .isActive(true)
                .build());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", coupon.getId(), "code", coupon.getCode()));
    }
}
