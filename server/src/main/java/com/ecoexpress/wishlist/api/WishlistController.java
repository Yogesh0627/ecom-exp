package com.ecoexpress.wishlist.api;

import com.ecoexpress.common.security.AuthenticatedUser;
import com.ecoexpress.wishlist.domain.Wishlist;
import com.ecoexpress.wishlist.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The signed-in user's wishlist. Scoped to their own id; no wishlist-by-id route. */
@Tag(name = "Wishlist")
@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    public record AddRequest(@NotNull UUID variantId, String note) {}

    public record WishlistItemView(
            UUID variantId, String sku, String productName, String productSlug,
            BigDecimal price, String note) {}

    @Operation(summary = "Get my wishlist")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> get(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(view(wishlistService.getOrCreate(user.getId())));
    }

    // @Transactional so view() can walk the lazy variant -> product association while the
    // session is still open. Without it, the first add (which creates a fresh wishlist whose
    // new item's product is not eagerly loaded) throws LazyInitializationException.
    @Operation(summary = "Add an item")
    @PostMapping("/items")
    @Transactional
    public ResponseEntity<Map<String, Object>> add(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddRequest r) {
        return ResponseEntity.ok(view(wishlistService.add(user.getId(), r.variantId(), r.note())));
    }

    @Operation(summary = "Remove an item")
    @DeleteMapping("/items/{variantId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> remove(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID variantId) {
        return ResponseEntity.ok(view(wishlistService.remove(user.getId(), variantId)));
    }

    private Map<String, Object> view(Wishlist w) {
        List<WishlistItemView> items = w.getItems().stream().map(i -> new WishlistItemView(
                i.getVariant().getId(),
                i.getVariant().getSku(),
                i.getVariant().getProduct().getName(),
                i.getVariant().getProduct().getSlug(),
                i.getVariant().getPrice(),
                i.getNote())).toList();
        return Map.of("id", w.getId(), "itemCount", items.size(), "items", items);
    }
}
