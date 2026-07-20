package com.ecoexpress.wishlist.service;

import com.ecoexpress.catalog.domain.ProductVariant;
import com.ecoexpress.catalog.repository.ProductVariantRepository;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.identity.domain.User;
import com.ecoexpress.identity.repository.UserRepository;
import com.ecoexpress.wishlist.domain.Wishlist;
import com.ecoexpress.wishlist.domain.WishlistItem;
import com.ecoexpress.wishlist.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The user's default wishlist. Every operation is scoped to the caller's own id — there is no
 * wishlist-by-id route, so one user can never read or mutate another's saved items.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;

    @Transactional
    public Wishlist getOrCreate(UUID userId) {
        return wishlistRepository.findDefaultForUser(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new NotFoundException("No user " + userId));
                    return wishlistRepository.save(Wishlist.builder()
                            .user(user).name("My Wishlist").isDefault(true).build());
                });
    }

    /** Adding an already-saved variant is a no-op, not a duplicate. */
    @Transactional
    public Wishlist add(UUID userId, UUID variantId, String note) {
        Wishlist wishlist = getOrCreate(userId);
        boolean present = wishlist.getItems().stream()
                .anyMatch(i -> i.getVariant().getId().equals(variantId));
        if (present) {
            return wishlist;
        }
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new NotFoundException("No variant " + variantId));
        wishlist.getItems().add(WishlistItem.builder()
                .wishlist(wishlist).variant(variant).note(note).build());
        return wishlistRepository.save(wishlist);
    }

    @Transactional
    public Wishlist remove(UUID userId, UUID variantId) {
        Wishlist wishlist = getOrCreate(userId);
        wishlist.getItems().removeIf(i -> i.getVariant().getId().equals(variantId));
        return wishlistRepository.save(wishlist);
    }
}
