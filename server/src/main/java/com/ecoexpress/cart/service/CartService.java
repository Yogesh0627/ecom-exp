package com.ecoexpress.cart.service;

import com.ecoexpress.cart.domain.Cart;
import com.ecoexpress.cart.domain.CartItem;
import com.ecoexpress.cart.domain.CartStatus;
import com.ecoexpress.cart.dto.CartDtos.CartItemResponse;
import com.ecoexpress.cart.dto.CartDtos.CartResponse;
import com.ecoexpress.cart.repository.CartRepository;
import com.ecoexpress.catalog.domain.ProductStatus;
import com.ecoexpress.catalog.domain.ProductVariant;
import com.ecoexpress.catalog.repository.ProductVariantRepository;
import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.identity.domain.User;
import com.ecoexpress.identity.repository.UserRepository;
import com.ecoexpress.inventory.exception.InsufficientStockException;
import com.ecoexpress.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cart operations.
 *
 * <p><b>The cart does not reserve stock.</b> Availability is checked when adding, and
 * again at checkout, but units are only reserved once an order is placed. Reserving on
 * add-to-cart would let anyone freeze the whole catalog by filling a cart and walking
 * away, and it makes every abandoned cart a stockout. The cost is that a cart can go
 * stale — which is why {@link #getCart} reports what is no longer available rather than
 * discovering it at checkout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final NutritionCalculator nutritionCalculator;

    @Transactional
    public CartResponse addItem(UUID userId, UUID variantId, int qty) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new NotFoundException("No variant " + variantId));

        requireSellable(variant);

        Cart cart = getOrCreateActiveCart(userId);

        CartItem existing = cart.getItems().stream()
                .filter(i -> i.getVariant().getId().equals(variantId))
                .findFirst()
                .orElse(null);

        // Check the TOTAL after the add, not just the delta — otherwise adding 1 unit
        // ten times sneaks past a stock level of 3.
        int desired = existing == null ? qty : existing.getQty() + qty;
        int available = inventoryService.availableFor(variantId);
        if (available < desired) {
            throw new InsufficientStockException(
                    "Only " + available + " left; your cart would need " + desired + ".");
        }

        if (existing != null) {
            existing.setQty(desired);
        } else {
            cart.getItems().add(CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .qty(qty)
                    .unitPriceSnapshot(variant.getPrice())
                    .build());
        }

        cartRepository.save(cart);
        return toResponse(cart);
    }

    /** Sets an exact quantity. Zero removes the line. */
    @Transactional
    public CartResponse updateItem(UUID userId, UUID variantId, int qty) {
        Cart cart = requireActiveCart(userId);
        CartItem item = cart.getItems().stream()
                .filter(i -> i.getVariant().getId().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("That item is not in your cart."));

        if (qty == 0) {
            cart.getItems().remove(item);
            cartRepository.save(cart);
            return toResponse(cart);
        }

        int available = inventoryService.availableFor(variantId);
        if (available < qty) {
            throw new InsufficientStockException("Only " + available + " left.");
        }
        item.setQty(qty);
        cartRepository.save(cart);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(UUID userId, UUID variantId) {
        return updateItem(userId, variantId, 0);
    }

    @Transactional
    public void clear(UUID userId) {
        Cart cart = requireActiveCart(userId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    @Transactional
    public CartResponse getCart(UUID userId) {
        return toResponse(getOrCreateActiveCart(userId));
    }

    /**
     * Folds an anonymous cart into the user's cart at sign-in.
     *
     * <p>Quantities are summed, then clamped to available stock rather than rejected: a
     * merge that throws would leave the customer signed in with no cart and no
     * explanation. The response reports what could not be honoured.
     */
    @Transactional
    public CartResponse mergeSessionCart(UUID userId, String sessionKey) {
        Cart anon = cartRepository.findBySessionKeyAndStatus(sessionKey, CartStatus.ACTIVE)
                .orElse(null);
        if (anon == null || anon.getItems().isEmpty()) {
            return getCart(userId);
        }

        Cart target = getOrCreateActiveCart(userId);
        for (CartItem src : new ArrayList<>(anon.getItems())) {
            UUID variantId = src.getVariant().getId();
            CartItem existing = target.getItems().stream()
                    .filter(i -> i.getVariant().getId().equals(variantId))
                    .findFirst().orElse(null);

            int desired = (existing == null ? 0 : existing.getQty()) + src.getQty();
            int available = inventoryService.availableFor(variantId);
            int finalQty = Math.min(desired, available);

            if (finalQty <= 0) {
                log.debug("Dropping {} from merged cart: nothing available", variantId);
                continue;
            }
            if (finalQty < desired) {
                log.info("Clamped {} to {} during cart merge (wanted {})", variantId, finalQty, desired);
            }

            if (existing != null) {
                existing.setQty(finalQty);
            } else {
                target.getItems().add(CartItem.builder()
                        .cart(target)
                        .variant(src.getVariant())
                        .qty(finalQty)
                        .unitPriceSnapshot(src.getUnitPriceSnapshot())
                        .build());
            }
        }

        // Retire the anonymous cart so the one-active-per-session index stays satisfied.
        anon.setStatus(CartStatus.CONVERTED);
        anon.getItems().clear();
        cartRepository.save(anon);
        cartRepository.save(target);

        return toResponse(target);
    }

    private void requireSellable(ProductVariant variant) {
        if (!Boolean.TRUE.equals(variant.getIsActive())) {
            throw new BadRequestException("That item is no longer sold.");
        }
        // A DRAFT or ARCHIVED product must not be buyable via a direct variant id, even
        // though it is hidden from listings.
        if (variant.getProduct().getStatus() != ProductStatus.ACTIVE) {
            throw new BadRequestException("That item is not available.");
        }
    }

    private Cart requireActiveCart(UUID userId) {
        return cartRepository.findByUserAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Your cart is empty."));
    }

    private Cart getOrCreateActiveCart(UUID userId) {
        return cartRepository.findByUserAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new NotFoundException("No user " + userId));
                    return cartRepository.save(Cart.builder()
                            .user(user)
                            .status(CartStatus.ACTIVE)
                            .build());
                });
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = new ArrayList<>();
        List<UUID> unavailable = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean priceChanged = false;

        for (CartItem item : cart.getItems()) {
            ProductVariant v = item.getVariant();
            int available = inventoryService.availableFor(v.getId());
            if (available < item.getQty()) {
                unavailable.add(v.getId());
            }
            if (item.priceChanged()) {
                priceChanged = true;
            }
            subtotal = subtotal.add(item.lineTotal());

            items.add(new CartItemResponse(
                    item.getId(), v.getId(), v.getSku(),
                    v.getProduct().getName(), v.getName(), v.getProduct().getSlug(),
                    v.primaryImage() == null ? null : v.primaryImage().getUrl(),
                    item.getQty(), v.getPrice(), item.lineTotal(), v.getWeightGrams(),
                    item.priceChanged(), item.getUnitPriceSnapshot(), available));
        }

        return new CartResponse(
                cart.getId(), items, cart.totalUnits(),
                subtotal.setScale(2, java.math.RoundingMode.HALF_UP), "INR",
                priceChanged, unavailable,
                nutritionCalculator.summarise(cart.getItems()));
    }
}
