package com.ecoexpress.catalog.repository;

import com.ecoexpress.catalog.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findBySku(String sku);

    Optional<ProductVariant> findByBarcode(String barcode);

    boolean existsBySku(String sku);

    List<ProductVariant> findByProductId(UUID productId);

    /**
     * Clears the default flag across a product's variants.
     *
     * <p>Needed before setting a new default: {@code variants_one_default_per_product}
     * is a unique index, so setting the new one first would collide with the old.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductVariant v SET v.isDefault = false WHERE v.product.id = :productId")
    void clearDefaultForProduct(@Param("productId") UUID productId);

    /**
     * Best-effort match of a free-text ingredient name (e.g. from a fridge scan) to a sellable
     * variant, by case-insensitive substring on the product name. Only active, in-catalog
     * variants. LIMIT 1 via the derived {@code findFirst}. Intentionally simple — see the AI
     * architecture note on upgrading to embeddings if this is too coarse.
     */
    @Query("""
            SELECT v FROM ProductVariant v
            WHERE v.isActive = true
              AND v.product.status = com.ecoexpress.catalog.domain.ProductStatus.ACTIVE
              AND lower(v.product.name) LIKE lower(concat('%', :name, '%'))
            ORDER BY v.isDefault DESC
            """)
    java.util.List<ProductVariant> findByProductNameMatch(@Param("name") String name,
            org.springframework.data.domain.Pageable pageable);

    default Optional<ProductVariant> findFirstByProductNameContainingActive(String name) {
        var matches = findByProductNameMatch(name,
                org.springframework.data.domain.PageRequest.of(0, 1));
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    /**
     * Admin variant picker: match by SKU or product name across EVERY product status (a manager
     * receives stock for a DRAFT product too), newest products first. Not limited to ACTIVE unlike
     * the fridge-scan match above.
     */
    @Query("""
            SELECT v FROM ProductVariant v
            WHERE lower(v.sku) LIKE lower(concat('%', :q, '%'))
               OR lower(v.product.name) LIKE lower(concat('%', :q, '%'))
            ORDER BY v.product.name ASC
            """)
    List<ProductVariant> searchForAdmin(@Param("q") String q,
            org.springframework.data.domain.Pageable pageable);
}
