package com.ecoexpress.wishlist.repository;

import com.ecoexpress.wishlist.domain.Wishlist;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WishlistRepository extends JpaRepository<Wishlist, UUID> {

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product"})
    @Query("SELECT w FROM Wishlist w WHERE w.user.id = :userId AND w.isDefault = true")
    Optional<Wishlist> findDefaultForUser(@Param("userId") UUID userId);
}
