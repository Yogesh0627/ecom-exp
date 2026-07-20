package com.ecoexpress.catalog.repository;

import com.ecoexpress.catalog.domain.ProductContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductContentRepository extends JpaRepository<ProductContent, UUID> {

    /** The content row for a product, regardless of status (admin editing). */
    @Query("SELECT c FROM ProductContent c WHERE c.product.id = :productId")
    Optional<ProductContent> findByProductId(@Param("productId") UUID productId);

    /** The content row for a product only if PUBLISHED (storefront display). */
    @Query("""
            SELECT c FROM ProductContent c
            WHERE c.product.id = :productId AND c.status = com.ecoexpress.catalog.domain.ProductContentStatus.PUBLISHED
            """)
    Optional<ProductContent> findPublishedByProductId(@Param("productId") UUID productId);
}
