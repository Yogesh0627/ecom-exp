package com.ecoexpress.cart.api;

import com.ecoexpress.cart.dto.CartDtos.AddItemRequest;
import com.ecoexpress.cart.dto.CartDtos.CartResponse;
import com.ecoexpress.cart.dto.CartDtos.UpdateItemRequest;
import com.ecoexpress.cart.service.CartService;
import com.ecoexpress.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The signed-in user's cart.
 *
 * <p>Every route is scoped to the caller's own id from the JWT — there is deliberately no
 * {@code /carts/{id}} route. Taking a cart id from the client would mean checking
 * ownership on every call and getting it wrong once is an IDOR that exposes someone
 * else's basket.
 */
@Tag(name = "Cart")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Get the current cart with its nutrition summary")
    @GetMapping
    public ResponseEntity<CartResponse> get(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(cartService.getCart(user.getId()));
    }

    @Operation(summary = "Add an item to the cart")
    @PostMapping("/items")
    public ResponseEntity<CartResponse> add(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddItemRequest request) {
        return ResponseEntity.ok(cartService.addItem(user.getId(), request.variantId(), request.qty()));
    }

    @Operation(summary = "Set an item's quantity (0 removes it)")
    @PutMapping("/items/{variantId}")
    public ResponseEntity<CartResponse> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateItemRequest request) {
        return ResponseEntity.ok(cartService.updateItem(user.getId(), variantId, request.qty()));
    }

    @Operation(summary = "Remove an item")
    @DeleteMapping("/items/{variantId}")
    public ResponseEntity<CartResponse> remove(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID variantId) {
        return ResponseEntity.ok(cartService.removeItem(user.getId(), variantId));
    }

    @Operation(summary = "Empty the cart")
    @DeleteMapping
    public ResponseEntity<Void> clear(@AuthenticationPrincipal AuthenticatedUser user) {
        cartService.clear(user.getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Merge an anonymous cart into this user's cart after sign-in")
    @PostMapping("/merge")
    public ResponseEntity<CartResponse> merge(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String sessionKey) {
        return ResponseEntity.ok(cartService.mergeSessionCart(user.getId(), sessionKey));
    }
}
